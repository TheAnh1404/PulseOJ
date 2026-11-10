package com.vku.pulseoj.common.rmi;

import com.vku.pulseoj.common.dto.LoginRequestDTO;
import com.vku.pulseoj.common.dto.LoginResponseDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * [TV1] Primary RMI Service exposed by Master Server for Contestant Clients.
 * Handles authentication, problem loading, code submissions, and scoreboard retrieval.
 */
public interface IMasterSubmissionService extends Remote {

    String SERVICE_NAME = "PulseMasterSubmissionService";

    /**
     * Authenticates a contestant with credentials verified against Neon PostgreSQL.
     */
    LoginResponseDTO login(LoginRequestDTO request) throws RemoteException;

    /**
     * Retrieves the list of all active contest problems.
     */
    List<ProblemDTO> getAllProblems() throws RemoteException;

    /**
     * Retrieves detailed information and sample test cases for a specific problem.
     */
    ProblemDTO getProblemById(int problemId) throws RemoteException;

    /**
     * Submits a solution to the Master queue and registers client callback for real-time updates.
     */
    SubmissionDTO submitSolution(SubmissionDTO submission, IClientCallbackService callback) throws RemoteException;

    /**
     * Queries the latest status of a given submission.
     */
    SubmissionDTO getSubmissionStatus(int submissionId) throws RemoteException;

    /**
     * Retrieves submission history for a specific contestant.
     */
    List<SubmissionDTO> getSubmissionsByContestant(int contestantId) throws RemoteException;

    /**
     * Retrieves the current live ACM-ICPC Scoreboard.
     */
    List<ScoreboardRowDTO> getScoreboard() throws RemoteException;

    /**
     * Lightweight health check / keep-alive ping.
     */
    boolean ping() throws RemoteException;
}
