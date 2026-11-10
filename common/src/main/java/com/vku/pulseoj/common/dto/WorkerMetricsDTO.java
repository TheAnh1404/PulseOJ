package com.vku.pulseoj.common.dto;

import com.vku.pulseoj.common.enums.WorkerStatus;

import java.io.Serializable;

/**
 * Worker node telemetry and performance metrics transmitted via UDP Heartbeat.
 */
public class WorkerMetricsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String workerId;
    private String workerHost;
    private int workerRmiPort;
    private WorkerStatus status = WorkerStatus.IDLE;
    private double cpuLoadPercent;
    private long freeRamMb;
    private long totalRamMb;
    private int activeJobs;
    private int completedJobs;
    private long lastHeartbeatTimestamp;

    public WorkerMetricsDTO() {
        this.lastHeartbeatTimestamp = System.currentTimeMillis();
    }

    public WorkerMetricsDTO(String workerId, String workerHost, int workerRmiPort,
                            WorkerStatus status, double cpuLoadPercent,
                            long freeRamMb, long totalRamMb,
                            int activeJobs, int completedJobs) {
        this.workerId = workerId;
        this.workerHost = workerHost;
        this.workerRmiPort = workerRmiPort;
        this.status = status;
        this.cpuLoadPercent = cpuLoadPercent;
        this.freeRamMb = freeRamMb;
        this.totalRamMb = totalRamMb;
        this.activeJobs = activeJobs;
        this.completedJobs = completedJobs;
        this.lastHeartbeatTimestamp = System.currentTimeMillis();
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getWorkerHost() {
        return workerHost;
    }

    public void setWorkerHost(String workerHost) {
        this.workerHost = workerHost;
    }

    public int getWorkerRmiPort() {
        return workerRmiPort;
    }

    public void setWorkerRmiPort(int workerRmiPort) {
        this.workerRmiPort = workerRmiPort;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public void setStatus(WorkerStatus status) {
        this.status = status;
    }

    public double getCpuLoadPercent() {
        return cpuLoadPercent;
    }

    public void setCpuLoadPercent(double cpuLoadPercent) {
        this.cpuLoadPercent = cpuLoadPercent;
    }

    public long getFreeRamMb() {
        return freeRamMb;
    }

    public void setFreeRamMb(long freeRamMb) {
        this.freeRamMb = freeRamMb;
    }

    public long getTotalRamMb() {
        return totalRamMb;
    }

    public void setTotalRamMb(long totalRamMb) {
        this.totalRamMb = totalRamMb;
    }

    public int getActiveJobs() {
        return activeJobs;
    }

    public void setActiveJobs(int activeJobs) {
        this.activeJobs = activeJobs;
    }

    public int getCompletedJobs() {
        return completedJobs;
    }

    public void setCompletedJobs(int completedJobs) {
        this.completedJobs = completedJobs;
    }

    public long getLastHeartbeatTimestamp() {
        return lastHeartbeatTimestamp;
    }

    public void setLastHeartbeatTimestamp(long lastHeartbeatTimestamp) {
        this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
    }

    public long getUsedRamMb() {
        return Math.max(0, totalRamMb - freeRamMb);
    }

    public double getRamUsagePercent() {
        if (totalRamMb <= 0) return 0.0;
        return ((double) getUsedRamMb() / totalRamMb) * 100.0;
    }
}
