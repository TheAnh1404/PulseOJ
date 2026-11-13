package com.vku.pulseoj.backend.worker;

import com.vku.pulseoj.backend.discovery.UdpDiscoveryClient;
import com.vku.pulseoj.backend.discovery.UdpHeartbeatSender;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import com.vku.pulseoj.common.rmi.IMasterClusterService;
import com.vku.pulseoj.common.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

/**
 * [TV2] Worker Node Backend Controller.
 * Manages discovery, RMI registration into the Master cluster, and telemetry heartbeat sender.
 */
public class WorkerBackendController {

    private static final Logger logger = LoggerFactory.getLogger(WorkerBackendController.class);

    private final String workerId;
    private String workerHost;
    private int workerRmiPort = 0; // 0 means dynamic ephemeral port

    private JudgeWorkerServiceImpl workerService;
    private IMasterClusterService masterClusterService;
    private UdpHeartbeatSender heartbeatSender;

    private boolean isConnected = false;
    private volatile boolean demoSlowMode = true;
    private WorkerUiListener uiListener;

    public WorkerBackendController() {
        this.workerId = "Worker-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public WorkerBackendController(String workerId) {
        this.workerId = workerId;
    }

    public void setDemoSlowMode(boolean enabled) {
        this.demoSlowMode = enabled;
        if (workerService != null) {
            workerService.setDemoSlowMode(enabled);
        }
    }

    public boolean isDemoSlowMode() {
        return demoSlowMode;
    }

    public void setUiListener(WorkerUiListener listener) {
        this.uiListener = listener;
        if (workerService != null) {
            workerService.setUiListener(listener);
        }
    }

    /**
     * Connects to Master using specified host and port (or discovered via UDP).
     */
    public synchronized boolean connectToMaster(String masterHost, int masterRmiPort) {
        try {
            this.workerHost = NetworkUtils.configureRmiHostname();

            logger.info("Looking up Master Cluster Service at {}:{}...", masterHost, masterRmiPort);
            Registry masterRegistry = LocateRegistry.getRegistry(masterHost, masterRmiPort);
            masterClusterService = (IMasterClusterService) masterRegistry.lookup(IMasterClusterService.SERVICE_NAME);

            // Create worker RMI service
            workerService = new JudgeWorkerServiceImpl(workerId);
            workerService.setDemoSlowMode(demoSlowMode);
            workerService.setMasterClusterService(masterClusterService);
            if (uiListener != null) {
                workerService.setUiListener(uiListener);
            }

            // Start UDP Heartbeat telemetry
            heartbeatSender = new UdpHeartbeatSender(workerId, workerHost, workerRmiPort, masterHost);
            workerService.setHeartbeatSender(heartbeatSender);
            heartbeatSender.start();

            // Register with Master
            boolean registered = masterClusterService.registerWorker(workerId, workerHost, workerRmiPort, workerService);
            if (registered) {
                isConnected = true;
                logger.info("Worker [{}] successfully registered with Master!", workerId);
                if (uiListener != null) {
                    uiListener.onSandboxConsoleOutput("Worker [" + workerId + "] connected to Master (" + masterHost + ":" + masterRmiPort + ")");
                    uiListener.onWorkerStatusChanged(WorkerStatus.IDLE);
                }
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to connect worker to Master: {}", e.getMessage(), e);
            if (uiListener != null) {
                uiListener.onSandboxConsoleOutput("Connection failed: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Attempts automatic discovery of Master Server on LAN via UDP broadcast.
     */
    public UdpDiscoveryClient.DiscoveryResult autoDiscoverMaster(int timeoutMs) {
        return UdpDiscoveryClient.discoverMaster(timeoutMs);
    }

    /**
     * Graceful disconnect from Master.
     */
    public synchronized void disconnect() {
        if (!isConnected) return;
        try {
            if (masterClusterService != null) {
                masterClusterService.unregisterWorker(workerId);
            }
        } catch (Exception ignored) {}

        cleanupResources();
        logger.info("Worker [{}] disconnected gracefully.", workerId);
    }

    /**
     * Simulates an abrupt Worker crash (stops heartbeat and drops RMI immediately)
     * to demonstrate Master failover and task re-queueing.
     */
    public synchronized void simulateCrash() {
        logger.warn("SIMULATING WORKER CRASH for [{}]!", workerId);
        if (uiListener != null) {
            uiListener.onSandboxConsoleOutput(">>> CRASH SIMULATION: Worker killed abruptly. Heartbeat stopped. <<<");
            uiListener.onWorkerStatusChanged(WorkerStatus.OFFLINE);
        }
        cleanupResources();
    }

    private void cleanupResources() {
        if (heartbeatSender != null) {
            heartbeatSender.stop();
        }
        masterClusterService = null;
        if (workerService != null) {
            workerService.setMasterClusterService(null);
            try {
                UnicastRemoteObject.unexportObject(workerService, true);
            } catch (Exception ignored) {}
            workerService.shutdown();
        }
        isConnected = false;
    }

    public String getWorkerId() {
        return workerId;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
