package com.vku.pulseoj.common.rmi;

import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * [TV2] Remote RMI Service exposed by each Judge Worker node.
 * Invoked by Master to dispatch submission judging tasks and monitor worker state.
 */
public interface IJudgeWorkerService extends Remote {

    String SERVICE_PREFIX = "PulseJudgeWorker_";

    /**
     * Master dispatches a submission job to this Worker for compilation and sandboxed execution.
     * The worker will report incremental test results back to Master via IMasterClusterService.
     */
    void submitJob(SubmissionDTO submission, ProblemDTO problem, List<TestCaseDTO> testCases) throws RemoteException;

    /**
     * Queries the current operational status of the worker (IDLE, BUSY, OFFLINE).
     */
    WorkerStatus getStatus() throws RemoteException;

    /**
     * Request worker to abort an ongoing job (e.g. timeout or cancelled).
     */
    void abortJob(int submissionId) throws RemoteException;

    /**
     * Heartbeat ping to verify Worker responsiveness over RMI.
     */
    boolean ping() throws RemoteException;
}
