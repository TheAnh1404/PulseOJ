package com.vku.pulseoj.common.rmi;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * [TV3] Reverse RMI Callback Service hosted on each Contestant Client machine.
 * Master Server invokes these methods to stream real-time judge updates and scoreboard refreshes.
 */
public interface IClientCallbackService extends Remote {

    /**
     * Pushes real-time progress for an ongoing submission as each test case completes.
     */
    void onTestProgress(JudgeResultDTO result) throws RemoteException;

    /**
     * Pushes final verdict when judging completes for a submission.
     */
    void onSubmissionFinalVerdict(SubmissionDTO submission) throws RemoteException;

    /**
     * Broadcasts updated ACM-ICPC Scoreboard whenever a submission verdict alters the standings.
     */
    void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) throws RemoteException;

    /**
     * Broadcasts teacher/system messages or contest announcements to contestants.
     */
    void onSystemBroadcast(String message) throws RemoteException;
}
