package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.rmi.IClientCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * [TV3] Implementation of IClientCallbackService.
 * Hosted on Contestant machine to receive real-time push events from Master.
 */
public class ClientCallbackServiceImpl extends UnicastRemoteObject implements IClientCallbackService {

    private static final Logger logger = LoggerFactory.getLogger(ClientCallbackServiceImpl.class);

    private ClientUiListener uiListener;

    public ClientCallbackServiceImpl() throws RemoteException {
        super();
    }

    public void setUiListener(ClientUiListener listener) {
        this.uiListener = listener;
    }

    @Override
    public void onTestProgress(JudgeResultDTO result) throws RemoteException {
        logger.debug("Received test progress callback: sub #{} test {}/{} -> {}",
                result.getSubmissionId(), result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict());
        if (uiListener != null) {
            uiListener.onTestProgress(result);
        }
    }

    @Override
    public void onSubmissionFinalVerdict(SubmissionDTO submission) throws RemoteException {
        logger.info("Received final verdict callback for submission #{}: {}",
                submission.getId(), submission.getVerdict());
        if (uiListener != null) {
            uiListener.onFinalVerdict(submission);
        }
    }

    @Override
    public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) throws RemoteException {
        logger.info("Received live scoreboard broadcast ({} entries)", scoreboard.size());
        if (uiListener != null) {
            uiListener.onScoreboardUpdated(scoreboard);
        }
    }

    @Override
    public void onSystemBroadcast(String message) throws RemoteException {
        logger.info("System broadcast received: {}", message);
        if (uiListener != null) {
            uiListener.onBroadcastMessage(message);
        }
    }
}
