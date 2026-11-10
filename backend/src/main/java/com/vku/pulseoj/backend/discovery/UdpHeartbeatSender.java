package com.vku.pulseoj.backend.discovery;

import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TV4] UDP Heartbeat Telemetry Sender.
 * Gathers system CPU and RAM metrics and periodically transmits telemetry to Master on UDP port 8889.
 */
public class UdpHeartbeatSender implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UdpHeartbeatSender.class);

    public static final int HEARTBEAT_PORT = 8889;
    public static final int INTERVAL_MS = 2000;

    private final String workerId;
    private final String workerHost;
    private final int workerRmiPort;
    private final String masterHost;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile WorkerStatus currentStatus = WorkerStatus.IDLE;
    private volatile int activeJobs = 0;
    private volatile int completedJobs = 0;

    private DatagramSocket socket;
    private Thread senderThread;

    public UdpHeartbeatSender(String workerId, String workerHost, int workerRmiPort, String masterHost) {
        this.workerId = workerId;
        this.workerHost = workerHost;
        this.workerRmiPort = workerRmiPort;
        this.masterHost = masterHost;
    }

    public void setStatus(WorkerStatus status) {
        this.currentStatus = status;
    }

    public void incrementCompletedJobs() {
        this.completedJobs++;
    }

    public void setActiveJobs(int count) {
        this.activeJobs = count;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            try {
                socket = new DatagramSocket();
                senderThread = new Thread(this, "UdpHeartbeatSender-" + workerId);
                senderThread.setDaemon(true);
                senderThread.start();
                logger.info("UdpHeartbeatSender started for worker [{}] -> {}:{}", workerId, masterHost, HEARTBEAT_PORT);
            } catch (Exception e) {
                logger.error("Failed to start UdpHeartbeatSender: {}", e.getMessage());
                running.set(false);
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                WorkerMetricsDTO metrics = collectMetrics();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(metrics);
                }

                byte[] data = baos.toByteArray();
                InetAddress target = InetAddress.getByName(masterHost);
                DatagramPacket packet = new DatagramPacket(data, data.length, target, HEARTBEAT_PORT);
                socket.send(packet);

                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running.get()) break;
                logger.debug("Heartbeat transmission issue: {}", e.getMessage());
            }
        }
    }

    private WorkerMetricsDTO collectMetrics() {
        double cpuPercent = 0.0;
        long freeRamMb = 0;
        long totalRamMb = 0;

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            // Attempt reflection for com.sun.management.OperatingSystemMXBean methods
            try {
                Method cpuMethod = osBean.getClass().getMethod("getCpuLoad");
                Object val = cpuMethod.invoke(osBean);
                if (val instanceof Double d && d >= 0) {
                    cpuPercent = d * 100.0;
                }
            } catch (Exception ignored) {
                cpuPercent = Math.max(0.0, osBean.getSystemLoadAverage() * 10.0);
            }

            try {
                Method freeMemMethod = osBean.getClass().getMethod("getFreeMemorySize");
                Object freeVal = freeMemMethod.invoke(osBean);
                if (freeVal instanceof Long l) freeRamMb = l / (1024 * 1024);

                Method totalMemMethod = osBean.getClass().getMethod("getTotalMemorySize");
                Object totalVal = totalMemMethod.invoke(osBean);
                if (totalVal instanceof Long l) totalRamMb = l / (1024 * 1024);
            } catch (Exception fallback) {
                Runtime runtime = Runtime.getRuntime();
                freeRamMb = runtime.freeMemory() / (1024 * 1024);
                totalRamMb = runtime.totalMemory() / (1024 * 1024);
            }
        } catch (Exception e) {
            logger.debug("Metrics collection fallback: {}", e.getMessage());
        }

        return new WorkerMetricsDTO(
                workerId,
                workerHost,
                workerRmiPort,
                currentStatus,
                Math.round(cpuPercent * 10.0) / 10.0,
                freeRamMb,
                totalRamMb,
                activeJobs,
                completedJobs
        );
    }
}
