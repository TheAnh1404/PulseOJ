package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.backend.client.IcpcScoreboardCalculator;
import com.vku.pulseoj.backend.db.ContestantAccountDAO;
import com.vku.pulseoj.backend.db.ProblemDAO;
import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.LoginRequestDTO;
import com.vku.pulseoj.common.dto.LoginResponseDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.rmi.IClientCallbackService;
import com.vku.pulseoj.common.rmi.IMasterSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [TV1] Implementation of IMasterSubmissionService.
 * RMI Entry point for Contestant Clients.
 */
public class MasterSubmissionServiceImpl extends UnicastRemoteObject implements IMasterSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(MasterSubmissionServiceImpl.class);

    private final ContestantAccountDAO contestantDAO;
    private final ProblemDAO problemDAO;
    private final SubmissionDAO submissionDAO;
    private final SubmissionDispatcher dispatcher;
    private final long contestStartTime = System.currentTimeMillis();

    // Map submissionId -> Client callback stub
    private final Map<Integer, IClientCallbackService> submissionCallbacks = new ConcurrentHashMap<>();
    // Map contestantId -> Client callback stub
    private final Map<Integer, IClientCallbackService> clientCallbacks = new ConcurrentHashMap<>();

    public MasterSubmissionServiceImpl(ContestantAccountDAO contestantDAO,
                                      ProblemDAO problemDAO,
                                      SubmissionDAO submissionDAO,
                                      SubmissionDispatcher dispatcher) throws RemoteException {
        super();
        this.contestantDAO = contestantDAO;
        this.problemDAO = problemDAO;
        this.submissionDAO = submissionDAO;
        this.dispatcher = dispatcher;
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO request) throws RemoteException {
        logger.info("Login request received for user [{}] from {}", request.getUsername(), request.getClientHost());
        ContestantDTO contestant = contestantDAO.authenticate(request.getUsername(), request.getPassword());
        if (contestant != null) {
            logger.info("Contestant [{}] authenticated successfully.", contestant.getUsername());
            return LoginResponseDTO.ok("Welcome " + contestant.getFullName() + "!", contestant);
        } else {
            logger.warn("Authentication failed for user [{}]", request.getUsername());
            return LoginResponseDTO.fail("Invalid credentials or account locked.");
        }
    }

    @Override
    public List<ProblemDTO> getAllProblems() throws RemoteException {
        return problemDAO.getAllProblems();
    }

    @Override
    public ProblemDTO getProblemById(int problemId) throws RemoteException {
        return problemDAO.getProblemById(problemId, false);
    }

    @Override
    public SubmissionDTO submitSolution(SubmissionDTO submission, IClientCallbackService callback) throws RemoteException {
        try {
            SubmissionDTO saved = submissionDAO.saveSubmission(submission);
            if (callback != null) {
                submissionCallbacks.put(saved.getId(), callback);
                if (saved.getContestantId() != null) {
                    clientCallbacks.put(saved.getContestantId(), callback);
                }
            }
            dispatcher.enqueueSubmission(saved);
            return saved;
        } catch (Exception e) {
            logger.error("Failed to process submission: {}", e.getMessage(), e);
            throw new RemoteException("Submission failed: " + e.getMessage());
        }
    }

    @Override
    public SubmissionDTO getSubmissionStatus(int submissionId) throws RemoteException {
        return submissionDAO.getById(submissionId);
    }

    @Override
    public List<SubmissionDTO> getSubmissionsByContestant(int contestantId) throws RemoteException {
        return submissionDAO.getByContestant(contestantId);
    }

    @Override
    public List<ScoreboardRowDTO> getScoreboard() throws RemoteException {
        List<ContestantDTO> contestants = contestantDAO.getAllContestants();
        List<SubmissionDTO> submissions = submissionDAO.getAllSubmissions();
        return IcpcScoreboardCalculator.calculate(contestants, submissions, contestStartTime);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    public IClientCallbackService getCallbackForSubmission(int submissionId) {
        return submissionCallbacks.get(submissionId);
    }

    public Map<Integer, IClientCallbackService> getAllClientCallbacks() {
        return clientCallbacks;
    }

    public void removeSubmissionCallback(int submissionId) {
        submissionCallbacks.remove(submissionId);
    }

    public void removeClientCallback(int contestantId) {
        clientCallbacks.remove(contestantId);
    }
}
