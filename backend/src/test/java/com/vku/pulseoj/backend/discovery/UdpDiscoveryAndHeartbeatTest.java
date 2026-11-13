package com.vku.pulseoj.backend.discovery;

import com.vku.pulseoj.backend.db.ProblemDAO;
import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.backend.master.SubmissionDispatcher;
import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import com.vku.pulseoj.common.enums.WorkerStatus;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TV4] Integration tests for UDP LAN Auto-Discovery (:8888),
 * UDP Heartbeat Telemetry (:8889), and Watchdog Timeout Failover (>6000ms).
 */
public class UdpDiscoveryAndHeartbeatTest {

    @Test
    @DisplayName("1. UDP Discovery Client discovers Master IP & RMI Port via broadcast on port 8888")
    void testUdpDiscoveryBroadcastAndResponse() {
        List<NetworkPacketLogDTO> capturedPackets = new CopyOnWriteArrayList<>();
        UdpDiscoveryServer server = new UdpDiscoveryServer("192.168.1.99", 1099);
        server.setMonitorListener(new ClusterMonitorListener() {
            @Override
            public void onWorkerMetricsReceived(WorkerMetricsDTO metrics) {}

            @Override
            public void onWorkerTimeout(String workerId) {}

            @Override
            public void onPacketLogged(NetworkPacketLogDTO packetLog) {
                capturedPackets.add(packetLog);
            }
        });

        try {
            server.start();
            UdpDiscoveryClient.DiscoveryResult result = UdpDiscoveryClient.discoverMaster(1500);

            assertTrue(result.found(), "Master Server must be discovered via UDP broadcast");
            assertEquals("192.168.1.99", result.masterHost());
            assertEquals(1099, result.masterRmiPort());
            assertFalse(capturedPackets.isEmpty(), "Inspector listener must capture UDP-DISCOVERY packets");
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("2. UDP Heartbeat Sender transmits WorkerMetricsDTO to Watchdog on port 8889")
    void testUdpHeartbeatTelemetryDelivery() throws Exception {
        SubmissionDispatcher dispatcher = new SubmissionDispatcher(new ProblemDAO(), new SubmissionDAO());
        UdpHeartbeatWatchdog watchdog = new UdpHeartbeatWatchdog(dispatcher);

        CountDownLatch metricsLatch = new CountDownLatch(1);
        watchdog.setMonitorListener(new ClusterMonitorListener() {
            @Override
            public void onWorkerMetricsReceived(WorkerMetricsDTO metrics) {
                if ("Worker-UDP-01".equals(metrics.getWorkerId())) {
                    metricsLatch.countDown();
                }
            }

            @Override
            public void onWorkerTimeout(String workerId) {}

            @Override
            public void onPacketLogged(NetworkPacketLogDTO packetLog) {}
        });

        UdpHeartbeatSender sender = new UdpHeartbeatSender("Worker-UDP-01", "127.0.0.1", 1100, "127.0.0.1");
        sender.setStatus(WorkerStatus.IDLE);

        try {
            watchdog.start();
            sender.start();

            boolean received = metricsLatch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Watchdog must receive UDP Heartbeat within 3 seconds");

            WorkerMetricsDTO stored = watchdog.getLatestMetrics().get("Worker-UDP-01");
            assertNotNull(stored);
            assertEquals(WorkerStatus.IDLE, stored.getStatus());
            assertTrue(stored.getTotalRamMb() >= 0);
        } finally {
            sender.stop();
            watchdog.stop();
        }
    }

    @Test
    @DisplayName("3. Watchdog marks silent worker OFFLINE (>6000ms) and triggers Dispatcher failover")
    void testWatchdogTimeoutTriggersOfflineAndFailover() throws Exception {
        SubmissionDispatcher dispatcher = new SubmissionDispatcher(new ProblemDAO(), new SubmissionDAO());
        UdpHeartbeatWatchdog watchdog = new UdpHeartbeatWatchdog(dispatcher);

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        watchdog.setMonitorListener(new ClusterMonitorListener() {
            @Override
            public void onWorkerMetricsReceived(WorkerMetricsDTO metrics) {}

            @Override
            public void onWorkerTimeout(String workerId) {
                if ("Worker-Dead-01".equals(workerId)) {
                    timeoutLatch.countDown();
                }
            }

            @Override
            public void onPacketLogged(NetworkPacketLogDTO packetLog) {}
        });

        // Register dummy worker and place an in-flight submission on it
        dispatcher.registerWorker("Worker-Dead-01", "127.0.0.1", 1105, new IJudgeWorkerService() {
            @Override
            public void submitJob(SubmissionDTO s, ProblemDTO p, List<TestCaseDTO> t) throws RemoteException {}
            @Override
            public WorkerStatus getStatus() throws RemoteException { return WorkerStatus.BUSY; }
            @Override
            public void abortJob(int submissionId) throws RemoteException {}
            @Override
            public boolean ping() throws RemoteException { return true; }
        });

        SubmissionDTO inFlightSub = new SubmissionDTO(5001, 1, "student01", 1, "TWO_SUM", Language.JAVA, "class Main {}");
        var inFlightField = SubmissionDispatcher.class.getDeclaredField("inFlightTasks");
        inFlightField.setAccessible(true);
        ((java.util.Map<Integer, SubmissionDTO>) inFlightField.get(dispatcher)).put(inFlightSub.getId(), inFlightSub);

        var taskWorkerField = SubmissionDispatcher.class.getDeclaredField("taskToWorkerMap");
        taskWorkerField.setAccessible(true);
        ((java.util.Map<Integer, String>) taskWorkerField.get(dispatcher)).put(inFlightSub.getId(), "Worker-Dead-01");

        // Pre-populate stale heartbeat metric (7 seconds ago -> exceeds 6000ms threshold)
        WorkerMetricsDTO staleMetrics = new WorkerMetricsDTO(
                "Worker-Dead-01", "127.0.0.1", 1105, WorkerStatus.BUSY, 25.0, 1024, 4096, 1, 0
        );
        staleMetrics.setLastHeartbeatTimestamp(System.currentTimeMillis() - 7000L);
        watchdog.getLatestMetrics().put("Worker-Dead-01", staleMetrics);

        try {
            watchdog.start();
            boolean timedOut = timeoutLatch.await(3, TimeUnit.SECONDS);
            assertTrue(timedOut, "Watchdog timer must detect stale worker and invoke onWorkerTimeout");

            assertEquals(WorkerStatus.OFFLINE, staleMetrics.getStatus(), "Worker status must transition to OFFLINE");
            assertEquals(1, dispatcher.getQueueSize(), "In-flight submission must be recovered back to pending queue");
            assertEquals(Verdict.PENDING, inFlightSub.getVerdict());
        } finally {
            watchdog.stop();
        }
    }
}
