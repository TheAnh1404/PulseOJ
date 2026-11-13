package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.common.dto.*;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import com.vku.pulseoj.common.rmi.IClientCallbackService;
import com.vku.pulseoj.common.rmi.IMasterSubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TV3 Integration Test - Chặng 2]
 * Verifies RMI Network Contract, Bi-directional Callback (Server Push),
 * Asynchronous CompletableFuture invocations, and thread safety.
 */
public class ClientNetworkCallbackTest {

    private int mockPort;
    private Registry registry;
    private MockMasterService mockMaster;
    private ClientBackendController controller;

    @BeforeEach
    void setUp() throws Exception {
        // Allocate an ephemeral port to avoid conflicts
        try (ServerSocket s = new ServerSocket(0)) {
            mockPort = s.getLocalPort();
        }

        registry = LocateRegistry.createRegistry(mockPort);
        mockMaster = new MockMasterService();
        registry.rebind(IMasterSubmissionService.SERVICE_NAME, mockMaster);

        controller = new ClientBackendController();
    }

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.disconnect();
        }
        if (mockMaster != null) {
            try {
                UnicastRemoteObject.unexportObject(mockMaster, true);
            } catch (Exception ignored) {}
        }
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Stage 2 - Step 1: Connect to Master and verify RMI remote ping")
    void testConnectToMaster_Success() throws Exception {
        Boolean connected = controller.connectToMaster("127.0.0.1", mockPort).get(5, TimeUnit.SECONDS);

        assertTrue(connected, "Controller should successfully connect to Master RMI");
        assertTrue(controller.isConnected(), "isConnected() flag should be true");
    }

    @Test
    @DisplayName("Stage 2 - Step 2: Asynchronous login flow with ContestantDTO binding")
    void testAsyncLogin_Success() throws Exception {
        controller.connectToMaster("127.0.0.1", mockPort).get(5, TimeUnit.SECONDS);

        LoginResponseDTO loginResp = controller.loginAsync("student01", "password123").get(5, TimeUnit.SECONDS);

        assertNotNull(loginResp);
        assertTrue(loginResp.isSuccess());
        assertNotNull(controller.getCurrentContestant());
        assertEquals("student01", controller.getCurrentContestant().getUsername());
        assertEquals("Nguyen Van A", controller.getCurrentContestant().getFullName());
    }

    @Test
    @DisplayName("Stage 2 - Step 3: End-to-end RMI Callback (Server Push) for test progress, verdict, and scoreboard")
    void testBiDirectionalRmiCallback_FullLifecycle() throws Exception {
        controller.connectToMaster("127.0.0.1", mockPort).get(5, TimeUnit.SECONDS);
        controller.loginAsync("student01", "pass").get(5, TimeUnit.SECONDS);

        // Setup UI Listener with Countdown Latches to verify real-time Server Push events
        CountDownLatch progressLatch = new CountDownLatch(1);
        CountDownLatch verdictLatch = new CountDownLatch(1);
        CountDownLatch scoreboardLatch = new CountDownLatch(1);
        CountDownLatch broadcastLatch = new CountDownLatch(1);

        AtomicReference<JudgeResultDTO> capturedProgress = new AtomicReference<>();
        AtomicReference<SubmissionDTO> capturedVerdict = new AtomicReference<>();
        AtomicReference<List<ScoreboardRowDTO>> capturedScoreboard = new AtomicReference<>();
        AtomicReference<String> capturedBroadcast = new AtomicReference<>();

        controller.setUiListener(new ClientUiListener() {
            @Override
            public void onTestProgress(JudgeResultDTO result) {
                capturedProgress.set(result);
                progressLatch.countDown();
            }

            @Override
            public void onFinalVerdict(SubmissionDTO submission) {
                capturedVerdict.set(submission);
                verdictLatch.countDown();
            }

            @Override
            public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) {
                capturedScoreboard.set(scoreboard);
                scoreboardLatch.countDown();
            }

            @Override
            public void onBroadcastMessage(String message) {
                capturedBroadcast.set(message);
                broadcastLatch.countDown();
            }

            @Override
            public void onConnectionStateChanged(boolean connected, String message) {}
        });

        // 1. Client submits solution -> Master receives submission AND callbackStub
        ProblemDTO problem = new ProblemDTO(101, "TWO_SUM", "Two Sum", "Find two indices", 1000, 256);
        SubmissionDTO submitted = controller.submitSolutionAsync(problem, Language.JAVA, "public class Solution {}")
                .get(5, TimeUnit.SECONDS);

        assertNotNull(submitted);
        assertEquals(999, submitted.getId());
        assertNotNull(mockMaster.lastCallbackStub, "Master must receive the exported IClientCallbackService stub!");

        // 2. Simulate Master pushing test case progress (Server Push)
        JudgeResultDTO progressResult = new JudgeResultDTO(999, Verdict.AC, 3, 10, 15, 4096, "worker-01", false);
        mockMaster.lastCallbackStub.onTestProgress(progressResult);

        assertTrue(progressLatch.await(3, TimeUnit.SECONDS), "Client must receive test progress callback");
        assertEquals(3, capturedProgress.get().getCurrentTestIndex());
        assertEquals(Verdict.AC, capturedProgress.get().getVerdict());

        // 3. Simulate Master pushing final verdict (Server Push)
        SubmissionDTO finalSub = new SubmissionDTO(999, 1, "student01", 101, "TWO_SUM", Language.JAVA, "code");
        finalSub.setVerdict(Verdict.AC);
        finalSub.setExecutionTimeMs(45);
        mockMaster.lastCallbackStub.onSubmissionFinalVerdict(finalSub);

        assertTrue(verdictLatch.await(3, TimeUnit.SECONDS), "Client must receive final verdict callback");
        assertEquals(Verdict.AC, capturedVerdict.get().getVerdict());
        assertEquals(45, capturedVerdict.get().getExecutionTimeMs());

        // 4. Simulate Master broadcasting updated scoreboard
        ScoreboardRowDTO row = new ScoreboardRowDTO(1, 1, "student01", "Nguyen Van A", 1, 45);
        mockMaster.lastCallbackStub.onScoreboardUpdated(List.of(row));

        assertTrue(scoreboardLatch.await(3, TimeUnit.SECONDS), "Client must receive scoreboard broadcast");
        assertEquals(1, capturedScoreboard.get().size());
        assertEquals("student01", capturedScoreboard.get().get(0).getUsername());

        // 5. Simulate Teacher broadcasting an urgent announcement
        mockMaster.lastCallbackStub.onSystemBroadcast("Con 15 phut nua het gio!");

        assertTrue(broadcastLatch.await(3, TimeUnit.SECONDS), "Client must receive system broadcast announcement");
        assertEquals("Con 15 phut nua het gio!", capturedBroadcast.get());
    }

    @Test
    @DisplayName("Stage 2 - Step 4: Disconnect gracefully unexports callback object and clears state")
    void testDisconnect_CleanUp() throws Exception {
        controller.connectToMaster("127.0.0.1", mockPort).get(5, TimeUnit.SECONDS);
        controller.loginAsync("student01", "pass").get(5, TimeUnit.SECONDS);

        assertTrue(controller.isConnected());
        assertNotNull(controller.getCurrentContestant());

        controller.disconnect();

        assertFalse(controller.isConnected());
        assertNull(controller.getCurrentContestant());
    }

    // =========================================================================
    // Mock In-Memory Master RMI Service Implementation
    // =========================================================================
    public static class MockMasterService extends UnicastRemoteObject implements IMasterSubmissionService {
        public IClientCallbackService lastCallbackStub;
        public SubmissionDTO lastSubmission;

        public MockMasterService() throws RemoteException {
            super();
        }

        @Override
        public LoginResponseDTO login(LoginRequestDTO request) throws RemoteException {
            if ("student01".equals(request.getUsername())) {
                ContestantDTO contestant = new ContestantDTO(1, "student01", "Nguyen Van A", false, Instant.now());
                return LoginResponseDTO.ok("Welcome", contestant);
            }
            return LoginResponseDTO.fail("Invalid credentials");
        }

        @Override
        public List<ProblemDTO> getAllProblems() throws RemoteException {
            return List.of(new ProblemDTO(101, "TWO_SUM", "Two Sum", "Find sum", 1000, 256));
        }

        @Override
        public ProblemDTO getProblemById(int problemId) throws RemoteException {
            return new ProblemDTO(101, "TWO_SUM", "Two Sum", "Find sum", 1000, 256);
        }

        @Override
        public SubmissionDTO submitSolution(SubmissionDTO submission, IClientCallbackService callback) throws RemoteException {
            this.lastSubmission = submission;
            this.lastCallbackStub = callback;
            submission.setId(999);
            submission.setVerdict(Verdict.PENDING);
            return submission;
        }

        @Override
        public SubmissionDTO getSubmissionStatus(int submissionId) throws RemoteException {
            return lastSubmission;
        }

        @Override
        public List<SubmissionDTO> getSubmissionsByContestant(int contestantId) throws RemoteException {
            return (lastSubmission != null) ? List.of(lastSubmission) : List.of();
        }

        @Override
        public List<ScoreboardRowDTO> getScoreboard() throws RemoteException {
            return List.of();
        }

        @Override
        public boolean ping() throws RemoteException {
            return true;
        }
    }
}

