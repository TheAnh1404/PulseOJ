package com.vku.pulseoj.backend.discovery;

import com.vku.pulseoj.backend.master.SubmissionDispatcher;
import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TV4] UDP Heartbeat Watchdog & Failure Detector on Master Server.
 * Listens on UDP port 8889 for worker telemetry.
 * If a worker ceases heartbeats for >6 seconds, marks it OFFLINE and initiates failover.
 */
public class UdpHeartbeatWatchdog implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UdpHeartbeatWatchdog.class);

    public static final int HEARTBEAT_PORT = 8889;
    public static final long TIMEOUT_THRESHOLD_MS = 6000; // 6 seconds threshold

    private final SubmissionDispatcher dispatcher;
    private final Map<String, WorkerMetricsDTO> latestMetrics = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread receiverThread;
    private Thread watchdogThread;
    private ClusterMonitorListener monitorListener;

    public UdpHeartbeatWatchdog(SubmissionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void setMonitorListener(ClusterMonitorListener listener) {
        this.monitorListener = listener;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            try {
                socket = new DatagramSocket(HEARTBEAT_PORT);
                receiverThread = new Thread(this, "UdpHeartbeatReceiver-Thread");
                receiverThread.setDaemon(true);
                receiverThread.start();

                watchdogThread = new Thread(this::runWatchdogLoop, "UdpHeartbeatWatchdog-Timer");
                watchdogThread.setDaemon(true);
                watchdogThread.start();

                logger.info("UdpHeartbeatWatchdog active on port {}", HEARTBEAT_PORT);
            } catch (Exception e) {
                logger.error("Failed to start UdpHeartbeatWatchdog: {}", e.getMessage());
                running.set(false);
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiverThread != null) receiverThread.interrupt();
        if (watchdogThread != null) watchdogThread.interrupt();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                    Object obj = ois.readObject();
                    if (obj instanceof WorkerMetricsDTO metrics) {
                        metrics.setLastHeartbeatTimestamp(System.currentTimeMillis());
                        latestMetrics.put(metrics.getWorkerId(), metrics);

                        if (monitorListener != null) {
                            monitorListener.onWorkerMetricsReceived(metrics);
                            monitorListener.onPacketLogged(new NetworkPacketLogDTO(
                                    "UDP-HEARTBEAT",
                                    packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                                    "Master:" + HEARTBEAT_PORT,
                                    packet.getLength(),
                                    "Heartbeat from [" + metrics.getWorkerId() + "]",
                                    String.format("CPU: %.1f%% | RAM Free: %dMB", metrics.getCpuLoadPercent(), metrics.getFreeRamMb())
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                if (!running.get()) break;
                logger.debug("Heartbeat socket read error: {}", e.getMessage());
            }
        }
    }

    private void runWatchdogLoop() {
        while (running.get()) {
            try {
                Thread.sleep(1000);
                long now = System.currentTimeMillis();

                for (WorkerMetricsDTO metrics : latestMetrics.values()) {
                    if (metrics.getStatus() != WorkerStatus.OFFLINE) {
                        long elapsed = now - metrics.getLastHeartbeatTimestamp();
                        if (elapsed > TIMEOUT_THRESHOLD_MS) {
                            logger.warn("WATCHDOG ALERT: Worker [{}] missed heartbeat for {} ms (> {} ms). Marking OFFLINE.",
                                    metrics.getWorkerId(), elapsed, TIMEOUT_THRESHOLD_MS);

                            metrics.setStatus(WorkerStatus.OFFLINE);

                            // Trigger dispatcher failover
                            if (dispatcher != null) {
                                dispatcher.updateWorkerStatus(metrics.getWorkerId(), WorkerStatus.OFFLINE);
                            }

                            if (monitorListener != null) {
                                monitorListener.onWorkerTimeout(metrics.getWorkerId());
                            }
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Watchdog loop error: {}", e.getMessage());
            }
        }
    }

    public Map<String, WorkerMetricsDTO> getLatestMetrics() {
        return latestMetrics;
    }
}
