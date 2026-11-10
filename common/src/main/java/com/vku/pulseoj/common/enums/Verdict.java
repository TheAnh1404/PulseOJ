package com.vku.pulseoj.common.enums;

import java.io.Serializable;

/**
 * Standard verdicts for submission judging results in PulseOJ.
 */
public enum Verdict implements Serializable {
    PENDING("Pending in Queue"),
    RUNNING("Running Test Cases"),
    AC("Accepted"),
    WA("Wrong Answer"),
    TLE("Time Limit Exceeded"),
    RE("Runtime Error"),
    CE("Compilation Error");

    private final String description;

    Verdict(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinal() {
        return this != PENDING && this != RUNNING;
    }

    public boolean isAccepted() {
        return this == AC;
    }
}
