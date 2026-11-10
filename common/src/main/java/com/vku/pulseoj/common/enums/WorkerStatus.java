package com.vku.pulseoj.common.enums;

import java.io.Serializable;

/**
 * Health and operational status of a Judge Worker node in the cluster.
 */
public enum WorkerStatus implements Serializable {
    IDLE("Idle - Ready for Jobs"),
    BUSY("Busy - Judging Submission"),
    OFFLINE("Offline - Unreachable");

    private final String description;

    WorkerStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
