package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.backend.db.ProblemDAO;
import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import com.vku.pulseoj.common.enums.WorkerStatus;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SubmissionDispatcherTest {

    private ProblemDAO problemDAO;
    private SubmissionDAO submissionDAO;
    private SubmissionDispatcher dispatcher;

    @BeforeEach
    public void setUp() {
        problemDAO = new ProblemDAO();
        submissionDAO = new SubmissionDAO();
        dispatcher = new SubmissionDispatcher(problemDAO, submissionDAO);
    }

    @Test
    public void testWorkerSelectionByLowestCpuLoad() {
        MockWorker workerA = new MockWorker("worker-A");
        MockWorker workerB = new MockWorker("worker-B");

        dispatcher.registerWorker("worker-A", "192.168.1.10", 1099, workerA);
        dispatcher.registerWorker("worker-B", "192.168.1.11", 1099, workerB);

        // Simulate workerA having 75% CPU load, workerB having 15% CPU load
        dispatcher.updateWorkerMetrics("worker-A", 75.0);
        dispatcher.updateWorkerMetrics("worker-B", 15.0);

        SubmissionDispatcher.WorkerSession best = dispatcher.pickBestIdleWorker();
        assertNotNull(best);
        assertEquals("worker-B", best.workerId, "Should select worker with lowest CPU load");
    }

    @Test
    public void testDispatchToIdleWorker() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MockWorker worker = new MockWorker("worker-01") {
            @Override
            public void submitJob(SubmissionDTO sub, ProblemDTO prob, List<TestCaseDTO> tests) throws RemoteException {
                super.submitJob(sub, prob, tests);
                latch.countDown();
            }
        };

        dispatcher.registerWorker("worker-01", "127.0.0.1", 1099, worker);
        dispatcher.start();

        SubmissionDTO sub = new SubmissionDTO(1001, 1, "student01", 1, "TWO_SUM", Language.JAVA, "class Main {}");
        dispatcher.enqueueSubmission(sub);

        boolean reached = latch.await(3, TimeUnit.SECONDS);
        assertTrue(reached, "Worker should receive job within 3 seconds");
        assertEquals(1, worker.jobsReceived.get());

        dispatcher.stop();
    }

    @Test
    public void testAutoFailoverWhenWorkerDown() throws Exception {
        MockWorker worker = new MockWorker("worker-crash");
        dispatcher.registerWorker("worker-crash", "127.0.0.1", 1099, worker);

        SubmissionDTO sub = new SubmissionDTO(2002, 1, "student01", 1, "TWO_SUM", Language.JAVA, "class Main {}");

        // Emulate task in flight on worker-crash
        var inFlightField = SubmissionDispatcher.class.getDeclaredField("inFlightTasks");
        inFlightField.setAccessible(true);
        ((java.util.Map<Integer, SubmissionDTO>) inFlightField.get(dispatcher)).put(sub.getId(), sub);

        var taskWorkerField = SubmissionDispatcher.class.getDeclaredField("taskToWorkerMap");
        taskWorkerField.setAccessible(true);
        ((java.util.Map<Integer, String>) taskWorkerField.get(dispatcher)).put(sub.getId(), "worker-crash");

        // Worker goes down
        dispatcher.updateWorkerStatus("worker-crash", WorkerStatus.OFFLINE);

        // Task must be recovered into the queue as PENDING
        assertEquals(1, dispatcher.getQueueSize(), "Task should be re-enqueued after worker crash");
        assertEquals(Verdict.PENDING, sub.getVerdict());
    }

    @Test
    public void testPoisonPillMaxRetriesAbortion() {
        MockWorker worker = new MockWorker("worker-fragile");
        dispatcher.registerWorker("worker-fragile", "127.0.0.1", 1099, worker);

        SubmissionDTO poisonSub = new SubmissionDTO(3003, 1, "student01", 1, "TWO_SUM", Language.JAVA, "poison_code");

        // Simulate 4 successive crashes
        for (int i = 1; i <= 4; i++) {
            // Emulate task being dispatched to worker
            dispatcher.getActiveWorkers().get("worker-fragile").status = WorkerStatus.BUSY;
            // Place into inFlight
            try {
                var inFlightField = SubmissionDispatcher.class.getDeclaredField("inFlightTasks");
                inFlightField.setAccessible(true);
                ((java.util.Map<Integer, SubmissionDTO>) inFlightField.get(dispatcher)).put(poisonSub.getId(), poisonSub);

                var taskWorkerField = SubmissionDispatcher.class.getDeclaredField("taskToWorkerMap");
                taskWorkerField.setAccessible(true);
                ((java.util.Map<Integer, String>) taskWorkerField.get(dispatcher)).put(poisonSub.getId(), "worker-fragile");
            } catch (Exception e) {
                fail(e.getMessage());
            }

            dispatcher.handleWorkerDown("worker-fragile");
        }

        // After 4th crash (> 3 retries), poisonSub must have Verdict.RE and not be in queue
        assertEquals(Verdict.RE, poisonSub.getVerdict(), "Should abort poison pill submission with Verdict.RE");
        assertTrue(poisonSub.getCompilerOutput().contains("repeatedly crashed worker nodes"));
    }

    private static class MockWorker implements IJudgeWorkerService {
        final String workerId;
        final AtomicInteger jobsReceived = new AtomicInteger(0);

        MockWorker(String workerId) {
            this.workerId = workerId;
        }

        @Override
        public void submitJob(SubmissionDTO submission, ProblemDTO problem, List<TestCaseDTO> testCases) throws RemoteException {
            jobsReceived.incrementAndGet();
        }

        @Override
        public WorkerStatus getStatus() throws RemoteException {
            return WorkerStatus.IDLE;
        }

        @Override
        public void abortJob(int submissionId) throws RemoteException {
            // No-op for mock
        }

        @Override
        public boolean ping() throws RemoteException {
            return true;
        }
    }
}
