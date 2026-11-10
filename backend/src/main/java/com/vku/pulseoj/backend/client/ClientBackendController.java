package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.backend.discovery.UdpDiscoveryClient;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.LoginRequestDTO;
import com.vku.pulseoj.common.dto.LoginResponseDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.rmi.IMasterSubmissionService;
import com.vku.pulseoj.common.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [TV3] Contestant Client Backend Controller.
 * Asynchronously interacts with Master Server via RMI and hosts the callback listener.
 * Wraps every network invocation in CompletableFuture to guarantee UI responsiveness.
 */
public class ClientBackendController {

    private static final Logger logger = LoggerFactory.getLogger(ClientBackendController.class);

    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();

    private IMasterSubmissionService masterService;
    private ClientCallbackServiceImpl callbackService;
    private ContestantDTO currentContestant;
    private String clientHost;
    private boolean isConnected = false;

    private ClientUiListener uiListener;

    public ClientBackendController() {}

    public void setUiListener(ClientUiListener listener) {
        this.uiListener = listener;
        if (callbackService != null) {
            callbackService.setUiListener(listener);
        }
    }

    /**
     * Connects to Master Server RMI registry.
     */
    public CompletableFuture<Boolean> connectToMaster(String masterHost, int masterPort) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.clientHost = NetworkUtils.configureRmiHostname();
                logger.info("Connecting to Master at {}:{}...", masterHost, masterPort);

                Registry registry = LocateRegistry.getRegistry(masterHost, masterPort);
                masterService = (IMasterSubmissionService) registry.lookup(IMasterSubmissionService.SERVICE_NAME);

                // Ping to verify
                boolean alive = masterService.ping();
                if (!alive) throw new RuntimeException("Master did not respond to ping.");

                // Initialize callback service
                callbackService = new ClientCallbackServiceImpl();
                if (uiListener != null) {
                    callbackService.setUiListener(uiListener);
                }

                isConnected = true;
                if (uiListener != null) {
                    uiListener.onConnectionStateChanged(true, "Connected to Master (" + masterHost + ":" + masterPort + ")");
                }
                return true;
            } catch (Exception e) {
                logger.error("Failed to connect to Master RMI: {}", e.getMessage());
                if (uiListener != null) {
                    uiListener.onConnectionStateChanged(false, "Connection error: " + e.getMessage());
                }
                return false;
            }
        }, networkExecutor);
    }

    /**
     * Asynchronous login request.
     */
    public CompletableFuture<LoginResponseDTO> loginAsync(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            if (masterService == null) {
                return LoginResponseDTO.fail("Not connected to Master Server.");
            }
            try {
                LoginRequestDTO req = new LoginRequestDTO(username, password, clientHost);
                LoginResponseDTO resp = masterService.login(req);
                if (resp.isSuccess()) {
                    this.currentContestant = resp.getContestant();
                }
                return resp;
            } catch (Exception e) {
                logger.error("Login call failed: {}", e.getMessage());
                return LoginResponseDTO.fail("Network error: " + e.getMessage());
            }
        }, networkExecutor);
    }

    /**
     * Asynchronously loads problem list from Master.
     */
    public CompletableFuture<List<ProblemDTO>> fetchProblemsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (masterService == null) return List.of();
            try {
                return masterService.getAllProblems();
            } catch (Exception e) {
                logger.error("Failed to fetch problems: {}", e.getMessage());
                return List.of();
            }
        }, networkExecutor);
    }

    /**
     * Asynchronously submits code and passes the reverse RMI callback stub.
     */
    public CompletableFuture<SubmissionDTO> submitSolutionAsync(ProblemDTO problem, Language language, String sourceCode) {
        return CompletableFuture.supplyAsync(() -> {
            if (masterService == null) {
                throw new IllegalStateException("Not connected to Master Server.");
            }
            if (currentContestant == null) {
                throw new IllegalStateException("Not authenticated.");
            }

            try {
                SubmissionDTO sub = new SubmissionDTO(
                        null,
                        currentContestant.getId(),
                        currentContestant.getUsername(),
                        problem.getId(),
                        problem.getCode(),
                        language,
                        sourceCode
                );

                logger.info("Submitting code for [{}] ({}) to Master...", problem.getCode(), language);
                return masterService.submitSolution(sub, callbackService);
            } catch (Exception e) {
                logger.error("Submission failed: {}", e.getMessage());
                throw new RuntimeException("Submission failed: " + e.getMessage(), e);
            }
        }, networkExecutor);
    }

    /**
     * Asynchronously queries current ICPC scoreboard.
     */
    public CompletableFuture<List<ScoreboardRowDTO>> fetchScoreboardAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (masterService == null) return List.of();
            try {
                return masterService.getScoreboard();
            } catch (Exception e) {
                logger.error("Failed to fetch scoreboard: {}", e.getMessage());
                return List.of();
            }
        }, networkExecutor);
    }

    /**
     * Asynchronously queries submission history for current contestant.
     */
    public CompletableFuture<List<SubmissionDTO>> fetchSubmissionHistoryAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (masterService == null || currentContestant == null) return List.of();
            try {
                return masterService.getSubmissionsByContestant(currentContestant.getId());
            } catch (Exception e) {
                logger.error("Failed to fetch submission history: {}", e.getMessage());
                return List.of();
            }
        }, networkExecutor);
    }

    /**
     * UDP Auto-discovery of Master on LAN.
     */
    public CompletableFuture<UdpDiscoveryClient.DiscoveryResult> autoDiscoverMaster(int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> UdpDiscoveryClient.discoverMaster(timeoutMs), networkExecutor);
    }

    public synchronized void disconnect() {
        if (callbackService != null) {
            try {
                UnicastRemoteObject.unexportObject(callbackService, true);
            } catch (Exception ignored) {}
        }
        isConnected = false;
        currentContestant = null;
        masterService = null;
    }

    public ContestantDTO getCurrentContestant() {
        return currentContestant;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
