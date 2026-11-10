package com.vku.pulseoj.backend.worker;

import com.vku.pulseoj.backend.discovery.UdpHeartbeatSender;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import com.vku.pulseoj.common.rmi.IMasterClusterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [TV2] Implementation of IJudgeWorkerService.
 * Executes judging jobs received from Master and streams back incremental results.
 */
public class JudgeWorkerServiceImpl extends UnicastRemoteObject implements IJudgeWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(JudgeWorkerServiceImpl.class);

    private final String workerId;
    private final CodeSandboxExecutor sandbox;
    private final ExecutorService threadPool = Executors.newSingleThreadExecutor();

    private volatile WorkerStatus status = WorkerStatus.IDLE;
    private IMasterClusterService masterClusterService;
    private UdpHeartbeatSender heartbeatSender;
    private WorkerUiListener uiListener;

    public JudgeWorkerServiceImpl(String workerId) throws RemoteException {
        super();
        this.workerId = workerId;
        this.sandbox = new CodeSandboxExecutor(workerId);
        this.sandbox.setConsoleLogger(line -> {
            if (uiListener != null) {
                uiListener.onSandboxConsoleOutput(line);
            }
        });
    }

    public void setMasterClusterService(IMasterClusterService masterClusterService) {
        this.masterClusterService = masterClusterService;
    }

    public void setHeartbeatSender(UdpHeartbeatSender sender) {
        this.heartbeatSender = sender;
    }

    public void setUiListener(WorkerUiListener listener) {
        this.uiListener = listener;
    }

    @Override
    public void submitJob(SubmissionDTO submission, ProblemDTO problem, List<TestCaseDTO> testCases) throws RemoteException {
        logger.info("Received judging job: submission #{} ({})", submission.getId(), submission.getProblemCode());

        updateStatus(WorkerStatus.BUSY);
        if (uiListener != null) {
            uiListener.onJobReceived(submission);
        }

        // Asynchronously process the submission to not block the Master dispatcher thread
        threadPool.submit(() -> {
            try {
                SubmissionDTO completed = sandbox.executeSubmission(submission, problem, testCases, result -> {
                    // 1. Report test progress to Master
                    if (masterClusterService != null) {
                        try {
                            masterClusterService.reportTestProgress(result);
                        } catch (Exception e) {
                            logger.error("Failed to report test progress to Master: {}", e.getMessage());
                        }
                    }
                    // 2. Report test progress to Worker UI
                    if (uiListener != null) {
                        uiListener.onTestFinished(result);
                    }
                });

                // Report final completed verdict to Master
                if (masterClusterService != null) {
                    masterClusterService.reportFinalVerdict(completed);
                }

                if (heartbeatSender != null) {
                    heartbeatSender.incrementCompletedJobs();
                }

                if (uiListener != null) {
                    uiListener.onJobFinished(completed);
                }

            } catch (Exception e) {
                logger.error("Job execution failure for submission #{}: {}", submission.getId(), e.getMessage(), e);
            } finally {
                updateStatus(WorkerStatus.IDLE);
            }
        });
    }

    private void updateStatus(WorkerStatus newStatus) {
        this.status = newStatus;
        if (heartbeatSender != null) {
            heartbeatSender.setStatus(newStatus);
            heartbeatSender.setActiveJobs(newStatus == WorkerStatus.BUSY ? 1 : 0);
        }
        if (uiListener != null) {
            uiListener.onWorkerStatusChanged(newStatus);
        }
    }

    @Override
    public WorkerStatus getStatus() throws RemoteException {
        return status;
    }

    @Override
    public void abortJob(int submissionId) throws RemoteException {
        logger.warn("Abort job requested for submission #{}", submissionId);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    public void shutdown() {
        threadPool.shutdownNow();
    }
}
