package com.vku.pulseoj.common.rmi;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * [TV4 & TV1] Internal Cluster Coordination RMI Service hosted on Master Server.
 * Judge Workers invoke this service to dynamically join the cluster and stream back test verdicts.
 */
public interface IMasterClusterService extends Remote {

    String SERVICE_NAME = "PulseMasterClusterService";

    /**
     * Worker registers its presence and provides its RMI stub for receiving dispatch jobs.
     */
    boolean registerWorker(String workerId, String workerHost, int workerRmiPort, IJudgeWorkerService workerStub) throws RemoteException;

    /**
     * Worker notifies Master upon graceful shutdown.
     */
    void unregisterWorker(String workerId) throws RemoteException;

    /**
     * Worker streams real-time test execution progress back to Master.
     * Master forwards this to the corresponding contestant's IClientCallbackService.
     */
    void reportTestProgress(JudgeResultDTO result) throws RemoteException;

    /**
     * Worker reports the completed submission verdict and execution metrics.
     * Master persists the outcome into Neon PostgreSQL and updates the Scoreboard.
     */
    void reportFinalVerdict(SubmissionDTO completedSubmission) throws RemoteException;
}
