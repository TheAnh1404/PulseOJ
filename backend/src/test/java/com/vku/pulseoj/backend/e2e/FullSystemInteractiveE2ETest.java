package com.vku.pulseoj.backend.e2e;

import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.backend.client.ClientUiListener;
import com.vku.pulseoj.backend.discovery.ClusterMonitorListener;
import com.vku.pulseoj.backend.discovery.UdpDiscoveryClient;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.backend.master.MasterUiListener;
import com.vku.pulseoj.backend.worker.WorkerBackendController;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.LoginResponseDTO;
import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-system interactive E2E test suite verifying TV1, TV2, TV3, and TV4 workflows
 * including mid-job worker crash failover and concurrent distributed stress testing.
 */
public class FullSystemInteractiveE2ETest {

    private MasterBackendController master;
    private int rmiPort;
    private final List<WorkerBackendController> activeWorkers = new ArrayList<>();
    private final List<ClientBackendController> activeClients = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            rmiPort = ss.getLocalPort();
        }
        master = new MasterBackendController();
        master.startServer(rmiPort, new MasterUiListener() {
            @Override public void onSubmissionQueued(SubmissionDTO submission) {}
            @Override public void onSubmissionDispatched(int submissionId, String workerId) {}
            @Override public void onTestProgressUpdate(JudgeResultDTO result) {}
            @Override public void onSubmissionCompleted(SubmissionDTO submission) {}
            @Override public void onContestantUpdated(ContestantDTO contestant) {}
            @Override public void onServerLog(String message) {}
        }, new ClusterMonitorListener() {
            @Override public void onWorkerMetricsReceived(WorkerMetricsDTO metrics) {}
            @Override public void onWorkerTimeout(String workerId) {}
            @Override public void onPacketLogged(NetworkPacketLogDTO packetLog) {}
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        for (ClientBackendController c : activeClients) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
        activeClients.clear();
        for (WorkerBackendController w : activeWorkers) {
            try { w.disconnect(); } catch (Exception ignored) {}
        }
        activeWorkers.clear();
        if (master != null) {
            master.stopServer();
        }
        Thread.sleep(150);
    }

    @Test
    @DisplayName("1. TV1 + TV4 + TV3: Contestant CRUD/Lock, Problem/Test CRUD, UDP Discovery, Multi-verdict & Scoreboard")
    void testInteractiveContestantProblemUdpAndVerdicts() throws Exception {
        String tag = String.valueOf(System.currentTimeMillis() % 100000);
        String username = "e2e_user_" + tag;
        String probCode = "E2E_SUM_" + tag;

        // 1. UDP Auto-Discovery (:8888)
        UdpDiscoveryClient.DiscoveryResult discovery = UdpDiscoveryClient.discoverMaster(2000);
        assertTrue(discovery.found(), "Client/Worker must discover Master via UDP :8888");
        assertEquals(rmiPort, discovery.masterRmiPort());

        // 2. Contestant Management: create, duplicate check, lock -> fail login, unlock -> login OK
        ContestantDTO created = master.createContestant(username, "secret123", "E2E Contestant");
        assertNotNull(created.getId());
        assertThrows(Exception.class, () -> master.createContestant(username, "secret123", "Duplicate"));

        assertTrue(master.setContestantLock(created.getId(), true));

        ClientBackendController client = new ClientBackendController();
        activeClients.add(client);
        assertTrue(client.connectToMaster("127.0.0.1", rmiPort).get(5, TimeUnit.SECONDS));

        LoginResponseDTO lockedLogin = client.loginAsync(username, "secret123").get(5, TimeUnit.SECONDS);
        assertFalse(lockedLogin.isSuccess(), "Locked contestant must not be able to log in");

        assertTrue(master.setContestantLock(created.getId(), false));
        LoginResponseDTO okLogin = client.loginAsync(username, "secret123").get(5, TimeUnit.SECONDS);
        assertTrue(okLogin.isSuccess(), "Unlocked contestant must log in successfully");

        // 3. Problem + Hidden Test Case Management
        ProblemDTO prob = master.saveProblem(new ProblemDTO(
                null, probCode, "E2E Addition", "Read a and b, print a+b", 1000, 256));
        assertNotNull(prob.getId());
        master.addTestCase(new TestCaseDTO(null, prob.getId(), 1, "2 5", "7", true));
        master.addTestCase(new TestCaseDTO(null, prob.getId(), 2, "10 20", "30", false));

        List<ProblemDTO> clientProblems = client.fetchProblemsAsync().get(5, TimeUnit.SECONDS);
        ProblemDTO foundForClient = clientProblems.stream()
                .filter(p -> p.getId().equals(prob.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, foundForClient.getTestCases().size(), "Client must see only sample tests");

        ProblemDTO fullForWorker = master.getProblemById(prob.getId(), true);
        assertEquals(2, fullForWorker.getTestCases().size(), "Worker/Master must see both sample and hidden tests");

        // 4. Connect fast Worker
        WorkerBackendController worker = new WorkerBackendController("Worker-E2E-Fast");
        worker.setDemoSlowMode(false);
        activeWorkers.add(worker);
        assertTrue(worker.connectToMaster("127.0.0.1", rmiPort));

        // 5. Submit WA then AC and verify callback progress + ICPC Scoreboard penalty
        CountDownLatch waLatch = new CountDownLatch(1);
        AtomicReference<SubmissionDTO> waVerdict = new AtomicReference<>();
        CountDownLatch acLatch = new CountDownLatch(1);
        AtomicReference<SubmissionDTO> acVerdict = new AtomicReference<>();
        List<JudgeResultDTO> testUpdates = new CopyOnWriteArrayList<>();

        client.setUiListener(new ClientUiListener() {
            @Override public void onTestProgress(JudgeResultDTO result) {
                testUpdates.add(result);
            }
            @Override public void onFinalVerdict(SubmissionDTO submission) {
                if (submission.getVerdict() == Verdict.WA) {
                    waVerdict.set(submission);
                    waLatch.countDown();
                } else if (submission.getVerdict() == Verdict.AC) {
                    acVerdict.set(submission);
                    acLatch.countDown();
                }
            }
            @Override public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) {}
            @Override public void onBroadcastMessage(String message) {}
            @Override public void onConnectionStateChanged(boolean connected, String message) {}
        });

        String waCode = "public class Main { public static void main(String[] a) { System.out.println(999); } }";
        client.submitSolutionAsync(foundForClient, Language.JAVA, waCode).get(5, TimeUnit.SECONDS);
        assertTrue(waLatch.await(15, TimeUnit.SECONDS), "WA submission must complete");
        assertEquals(Verdict.WA, waVerdict.get().getVerdict());

        String acCode = "import java.util.*; public class Main { public static void main(String[] a) { " +
                "Scanner sc = new Scanner(System.in); System.out.println(sc.nextLong() + sc.nextLong()); } }";
        client.submitSolutionAsync(foundForClient, Language.JAVA, acCode).get(5, TimeUnit.SECONDS);
        assertTrue(acLatch.await(15, TimeUnit.SECONDS), "AC submission must complete");
        assertEquals(Verdict.AC, acVerdict.get().getVerdict());
        assertTrue(testUpdates.size() >= 3, "Must receive incremental test callbacks across WA + AC runs");

        // 6. Verify Mock DAO ordering (ASC for contestants/problems, DESC for submissions)
        List<ContestantDTO> allContestants = master.getAllContestants();
        assertTrue(allContestants.get(0).getId() < allContestants.get(allContestants.size() - 1).getId());
        List<SubmissionDTO> allSubs = master.getAllSubmissions();
        assertTrue(allSubs.size() >= 2 && allSubs.get(0).getId() > allSubs.get(1).getId());
    }

    @Test
    @DisplayName("2. TV2 + TV4 + TV1: Mid-job Worker Crash does NOT falsely report AC and Auto-Failover completes on backup Worker")
    void testMidJobWorkerCrashAutoFailover() throws Exception {
        // 1. Connect ONLY slow Worker-Fail-A first so it picks up the submission
        WorkerBackendController workerFailA = new WorkerBackendController("Worker-Fail-A");
        workerFailA.setDemoSlowMode(true); // 400ms delay per test case gives plenty of time after Test #1
        activeWorkers.add(workerFailA);
        assertTrue(workerFailA.connectToMaster("127.0.0.1", rmiPort));

        // 2. Connect Client & login
        ClientBackendController client = new ClientBackendController();
        activeClients.add(client);
        assertTrue(client.connectToMaster("127.0.0.1", rmiPort).get(5, TimeUnit.SECONDS));
        assertTrue(client.loginAsync("student01", "password123").get(5, TimeUnit.SECONDS).isSuccess());

        ProblemDTO twoSum = client.fetchProblemsAsync().get(5, TimeUnit.SECONDS).get(0);

        CountDownLatch test1DoneLatch = new CountDownLatch(1);
        CountDownLatch finalBackupAcLatch = new CountDownLatch(1);
        List<SubmissionDTO> finalVerdictsReceived = new CopyOnWriteArrayList<>();

        client.setUiListener(new ClientUiListener() {
            @Override public void onTestProgress(JudgeResultDTO result) {
                if ("Worker-Fail-A".equals(result.getWorkerId()) && result.getCurrentTestIndex() == 1) {
                    test1DoneLatch.countDown();
                }
            }
            @Override public void onFinalVerdict(SubmissionDTO submission) {
                finalVerdictsReceived.add(submission);
                finalBackupAcLatch.countDown();
            }
            @Override public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) {}
            @Override public void onBroadcastMessage(String message) {}
            @Override public void onConnectionStateChanged(boolean connected, String message) {}
        });

        String acCode = "import java.util.*; public class Main { public static void main(String[] a) { " +
                "Scanner sc = new Scanner(System.in); System.out.println(sc.nextLong() + sc.nextLong()); } }";

        SubmissionDTO queued = client.submitSolutionAsync(twoSum, Language.JAVA, acCode).get(5, TimeUnit.SECONDS);
        assertNotNull(queued.getId());

        // 3. Wait for Worker-Fail-A to complete Test #1 (entering 400ms sleep before Test #2), then CRASH it!
        assertTrue(test1DoneLatch.await(15, TimeUnit.SECONDS), "Worker-Fail-A should complete Test #1");
        workerFailA.simulateCrash();

        // 4. Register backup Worker-Backup-B to take over the failed-over task when Watchdog (>6s) triggers
        WorkerBackendController workerBackupB = new WorkerBackendController("Worker-Backup-B");
        workerBackupB.setDemoSlowMode(false);
        activeWorkers.add(workerBackupB);
        assertTrue(workerBackupB.connectToMaster("127.0.0.1", rmiPort));

        // 5. Wait up to 20s for Watchdog (>6000ms) -> Failover -> Worker-Backup-B completion
        assertTrue(finalBackupAcLatch.await(20, TimeUnit.SECONDS),
                "In-flight submission must recover via Auto-Failover and complete on Worker-Backup-B");
        assertEquals(1, finalVerdictsReceived.size(), "Crashed worker must NOT report a spurious final verdict");
        SubmissionDTO completed = finalVerdictsReceived.get(0);
        assertEquals(Verdict.AC, completed.getVerdict());
        assertEquals("Worker-Backup-B", completed.getWorkerId(),
                "Final verdict must come from backup worker Worker-Backup-B after failover");
    }

    @Test
    @DisplayName("3. TV4 + TV1: Distributed Stress Test (10 concurrent submissions via submitLocalSubmission across 2 Workers)")
    void testDistributedStressSubmissionFlow() throws Exception {
        WorkerBackendController w1 = new WorkerBackendController("Worker-Stress-1");
        w1.setDemoSlowMode(false);
        WorkerBackendController w2 = new WorkerBackendController("Worker-Stress-2");
        w2.setDemoSlowMode(false);
        activeWorkers.add(w1);
        activeWorkers.add(w2);
        assertTrue(w1.connectToMaster("127.0.0.1", rmiPort));
        assertTrue(w2.connectToMaster("127.0.0.1", rmiPort));

        ProblemDTO prob = master.getAllProblems().get(0);
        ContestantDTO student = master.getAllContestants().get(0);
        String acCode = "import java.util.*; public class Main { public static void main(String[] a) { " +
                "Scanner sc = new Scanner(System.in); System.out.println(sc.nextLong() + sc.nextLong()); } }";

        List<Integer> submittedIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SubmissionDTO sub = new SubmissionDTO(
                    null, student.getId(), student.getUsername(),
                    prob.getId(), prob.getCode(), Language.JAVA, acCode);
            SubmissionDTO saved = master.submitLocalSubmission(sub);
            assertNotNull(saved.getId(), "submitLocalSubmission must assign a persisted ID before queueing");
            submittedIds.add(saved.getId());
        }

        // Poll until all 10 reach AC (max 25s)
        long deadline = System.currentTimeMillis() + 25_000;
        boolean allAc = false;
        while (System.currentTimeMillis() < deadline) {
            allAc = submittedIds.stream()
                    .map(id -> master.getSubmissionById(id))
                    .allMatch(s -> s != null && s.getVerdict() == Verdict.AC && s.getWorkerId() != null);
            if (allAc) break;
            Thread.sleep(250);
        }
        assertTrue(allAc, "All 10 stress-test submissions must be judged AC by the worker cluster");
    }
}
