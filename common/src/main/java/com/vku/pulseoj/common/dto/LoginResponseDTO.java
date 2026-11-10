package com.vku.pulseoj.common.dto;

import java.io.Serializable;

/**
 * Authentication response sent back from Master to Client.
 */
public class LoginResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private ContestantDTO contestant;
    private long serverTime;

    public LoginResponseDTO() {}

    public LoginResponseDTO(boolean success, String message, ContestantDTO contestant, long serverTime) {
        this.success = success;
        this.message = message;
        this.contestant = contestant;
        this.serverTime = serverTime;
    }

    public static LoginResponseDTO ok(String message, ContestantDTO contestant) {
        return new LoginResponseDTO(true, message, contestant, System.currentTimeMillis());
    }

    public static LoginResponseDTO fail(String message) {
        return new LoginResponseDTO(false, message, null, System.currentTimeMillis());
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ContestantDTO getContestant() {
        return contestant;
    }

    public void setContestant(ContestantDTO contestant) {
        this.contestant = contestant;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }
}
