package com.vku.pulseoj.backend.discovery;

import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;

/**
 * [TV4] Observer interface for cluster monitoring, telemetry, and packet inspector.
 */
public interface ClusterMonitorListener {

    /**
     * Emitted when a worker heartbeat is received and metrics are updated.
     */
    void onWorkerMetricsReceived(WorkerMetricsDTO metrics);

    /**
     * Emitted when a worker fails heartbeat checks and is marked offline.
     */
    void onWorkerTimeout(String workerId);

    /**
     * Emitted when a network packet is transmitted or received.
     */
    void onPacketLogged(NetworkPacketLogDTO packetLog);
}
