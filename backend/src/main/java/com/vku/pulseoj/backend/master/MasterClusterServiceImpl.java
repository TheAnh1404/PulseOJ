package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.rmi.IClientCallbackService;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import com.vku.pulseoj.common.rmi.IMasterClusterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * [TV4 & TV1] Implementation of IMasterClusterService.
 * Coordinates judge worker registrations and streams test execution progress.
 */
public class MasterClusterServiceImpl extends UnicastRemoteObject implements IMasterClusterService {

    private static final Logger logger = LoggerFactory.getLogger(MasterClusterServiceImpl.class);

    private final SubmissionDispatcher dispatcher;
    private final SubmissionDAO submissionDAO;
    private final MasterSubmissionServiceImpl masterSubmissionService;
    private MasterUiListener uiListener;

    public MasterClusterServiceImpl(SubmissionDispatcher dispatcher,
                                   SubmissionDAO submissionDAO,
                                   MasterSubmissionServiceImpl masterSubmissionService) throws RemoteException {
        super();
        this.dispatcher = dispatcher;
        this.submissionDAO = submissionDAO;
        this.masterSubmissionService = masterSubmissionService;
    }

    public void setUiListener(MasterUiListener listener) {
        this.uiListener = listener;
    }

    @Override
    public boolean registerWorker(String workerId, String workerHost, int workerRmiPort, IJudgeWorkerService workerStub) throws RemoteException {
        logger.info("Worker [{}] registered from {}:{}", workerId, workerHost, workerRmiPort);
        dispatcher.registerWorker(workerId, workerHost, workerRmiPort, workerStub);
        return true;
    }

    @Override
    public void unregisterWorker(String workerId) throws RemoteException {
        logger.info("Worker [{}] unregistered gracefully.", workerId);
        dispatcher.unregisterWorker(workerId);
    }

    @Override
    public void reportTestProgress(JudgeResultDTO result) throws RemoteException {
        logger.debug("Progress update: sub #{} test {}/{} -> {}",
                result.getSubmissionId(), result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict());

        // Notify UI
        if (uiListener != null) {
            uiListener.onTestProgressUpdate(result);
        }

        // Forward to client callback
        IClientCallbackService callback = masterSubmissionService.getCallbackForSubmission(result.getSubmissionId());
        if (callback != null) {
            try {
                callback.onTestProgress(result);
            } catch (Exception e) {
                logger.warn("Failed to push test progress to client: {}", e.getMessage());
            }
        }
    }

    @Override
    public void reportFinalVerdict(SubmissionDTO completedSubmission) throws RemoteException {
        logger.info("Final verdict for submission #{}: {} ({} ms, {} KB) judged by [{}]",
                completedSubmission.getId(), completedSubmission.getVerdict(),
                completedSubmission.getExecutionTimeMs(), completedSubmission.getMemoryUsedKb(),
                completedSubmission.getWorkerId());

        // 1. Update Neon PostgreSQL
        submissionDAO.updateVerdict(
                completedSubmission.getId(),
                completedSubmission.getVerdict(),
                completedSubmission.getExecutionTimeMs(),
                completedSubmission.getMemoryUsedKb(),
                completedSubmission.getCompilerOutput(),
                completedSubmission.getWorkerId()
        );

        // 2. Mark worker available again
        dispatcher.onTaskCompleted(completedSubmission.getId(), completedSubmission.getWorkerId());

        // 3. Notify Master UI
        if (uiListener != null) {
            uiListener.onSubmissionCompleted(completedSubmission);
        }

        // 4. Push final verdict to client callback
        IClientCallbackService callback = masterSubmissionService.getCallbackForSubmission(completedSubmission.getId());
        if (callback != null) {
            try {
                callback.onSubmissionFinalVerdict(completedSubmission);
            } catch (Exception e) {
                logger.warn("Failed to push final verdict to client: {}", e.getMessage());
            }
            masterSubmissionService.removeSubmissionCallback(completedSubmission.getId());
        }

        // 5. Broadcast updated scoreboard to all connected clients
        broadcastScoreboard();
    }

    private void broadcastScoreboard() {
        try {
            List<ScoreboardRowDTO> scoreboard = masterSubmissionService.getScoreboard();
            for (java.util.Map.Entry<Integer, IClientCallbackService> entry : masterSubmissionService.getAllClientCallbacks().entrySet()) {
                try {
                    entry.getValue().onScoreboardUpdated(scoreboard);
                } catch (RemoteException re) {
                    logger.info("Client stub for contestant #{} is disconnected. Removing dead callback.", entry.getKey());
                    masterSubmissionService.removeClientCallback(entry.getKey());
                } catch (Exception e) {
                    logger.debug("Non-critical error broadcasting scoreboard to client #{}: {}", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to broadcast updated scoreboard: {}", e.getMessage());
        }
    }
}
