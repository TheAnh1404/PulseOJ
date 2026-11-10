package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.backend.db.ContestantAccountDAO;
import com.vku.pulseoj.backend.db.NeonDataSourceConfig;
import com.vku.pulseoj.backend.db.ProblemDAO;
import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.backend.discovery.ClusterMonitorListener;
import com.vku.pulseoj.backend.discovery.UdpDiscoveryServer;
import com.vku.pulseoj.backend.discovery.UdpHeartbeatWatchdog;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.rmi.IMasterClusterService;
import com.vku.pulseoj.common.rmi.IMasterSubmissionService;
import com.vku.pulseoj.common.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * [TV1] Master Server Backend Controller.
 * Manages database pools, RMI Registry, Submission Dispatcher, and UDP services.
 * Pure backend class - ZERO javax.swing dependencies.
 */
public class MasterBackendController {

    private static final Logger logger = LoggerFactory.getLogger(MasterBackendController.class);

    private final ContestantAccountDAO contestantDAO;
    private final ProblemDAO problemDAO;
    private final SubmissionDAO submissionDAO;
    private final SubmissionDispatcher dispatcher;

    private MasterSubmissionServiceImpl masterSubmissionService;
    private MasterClusterServiceImpl masterClusterService;
    private UdpDiscoveryServer discoveryServer;
    private UdpHeartbeatWatchdog heartbeatWatchdog;

    private Registry rmiRegistry;
    private String serverIp;
    private int rmiPort = 1099;
    private boolean isRunning = false;

    public MasterBackendController() {
        // Initialize Neon PostgreSQL Connection Pool
        NeonDataSourceConfig.init();

        this.contestantDAO = new ContestantAccountDAO();
        this.problemDAO = new ProblemDAO();
        this.submissionDAO = new SubmissionDAO();
        this.dispatcher = new SubmissionDispatcher(problemDAO, submissionDAO);
    }

    public synchronized void startServer(int port, MasterUiListener uiListener, ClusterMonitorListener monitorListener) throws Exception {
        if (isRunning) return;

        this.rmiPort = port;
        this.serverIp = NetworkUtils.configureRmiHostname();

        // 1. Start Dispatcher
        dispatcher.setUiListener(uiListener);
        dispatcher.start();

        // 2. Export RMI Services
        masterSubmissionService = new MasterSubmissionServiceImpl(contestantDAO, problemDAO, submissionDAO, dispatcher);
        masterClusterService = new MasterClusterServiceImpl(dispatcher, submissionDAO, masterSubmissionService);
        masterClusterService.setUiListener(uiListener);

        try {
            rmiRegistry = LocateRegistry.createRegistry(rmiPort);
            logger.info("Created in-process RMI registry on port {}", rmiPort);
        } catch (Exception e) {
            rmiRegistry = LocateRegistry.getRegistry(rmiPort);
            logger.info("Using existing RMI registry on port {}", rmiPort);
        }

        rmiRegistry.rebind(IMasterSubmissionService.SERVICE_NAME, masterSubmissionService);
        rmiRegistry.rebind(IMasterClusterService.SERVICE_NAME, masterClusterService);
        logger.info("Bound RMI Services: {} and {}", IMasterSubmissionService.SERVICE_NAME, IMasterClusterService.SERVICE_NAME);

        // 3. Start UDP Discovery Server (8888)
        discoveryServer = new UdpDiscoveryServer(serverIp, rmiPort);
        discoveryServer.setMonitorListener(monitorListener);
        discoveryServer.start();

        // 4. Start UDP Heartbeat Watchdog (8889)
        heartbeatWatchdog = new UdpHeartbeatWatchdog(dispatcher);
        heartbeatWatchdog.setMonitorListener(monitorListener);
        heartbeatWatchdog.start();

        isRunning = true;
        if (uiListener != null) {
            uiListener.onServerLog("Master Server running on IP: " + serverIp + " (RMI: " + rmiPort + ")");
        }
    }

    public synchronized void stopServer() {
        if (!isRunning) return;

        if (discoveryServer != null) discoveryServer.stop();
        if (heartbeatWatchdog != null) heartbeatWatchdog.stop();
        if (dispatcher != null) dispatcher.stop();

        try {
            if (rmiRegistry != null) {
                rmiRegistry.unbind(IMasterSubmissionService.SERVICE_NAME);
                rmiRegistry.unbind(IMasterClusterService.SERVICE_NAME);
            }
        } catch (Exception ignored) {}

        NeonDataSourceConfig.close();
        isRunning = false;
        logger.info("Master Server stopped.");
    }

    // =========================================================================
    // Delegated Data Access Methods for UI (TV1)
    // =========================================================================

    public List<ContestantDTO> getAllContestants() {
        return contestantDAO.getAllContestants();
    }

    public ContestantDTO createContestant(String username, String password, String fullName) throws Exception {
        return contestantDAO.createContestant(username, password, fullName);
    }

    public boolean setContestantLock(int contestantId, boolean locked) {
        return contestantDAO.setLockStatus(contestantId, locked);
    }

    public List<ProblemDTO> getAllProblems() {
        return problemDAO.getAllProblems();
    }

    public ProblemDTO getProblemById(int problemId, boolean includeHidden) {
        return problemDAO.getProblemById(problemId, includeHidden);
    }

    public ProblemDTO saveProblem(ProblemDTO problem) throws Exception {
        return problemDAO.saveProblem(problem);
    }

    public TestCaseDTO addTestCase(TestCaseDTO testCase) throws Exception {
        return problemDAO.addTestCase(testCase);
    }

    public List<TestCaseDTO> getTestCases(int problemId, boolean includeHidden) {
        return problemDAO.getTestCasesForProblem(problemId, includeHidden);
    }

    public List<SubmissionDTO> getAllSubmissions() {
        return submissionDAO.getAllSubmissions();
    }

    public SubmissionDTO getSubmissionById(int submissionId) {
        return submissionDAO.getById(submissionId);
    }

    public List<ScoreboardRowDTO> getScoreboard() {
        try {
            if (masterSubmissionService != null) {
                return masterSubmissionService.getScoreboard();
            }
        } catch (Exception e) {
            logger.error("Failed to get scoreboard: {}", e.getMessage());
        }
        return List.of();
    }

    public String getServerIp() {
        return serverIp;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public SubmissionDispatcher getDispatcher() {
        return dispatcher;
    }

    public UdpHeartbeatWatchdog getHeartbeatWatchdog() {
        return heartbeatWatchdog;
    }
}
