package com.vku.pulseoj.common.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Data Transfer Object representing a Contestant account.
 */
public class ContestantDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String username;
    private String fullName;
    private boolean locked;
    private Instant createdAt;

    public ContestantDTO() {}

    public ContestantDTO(Integer id, String username, String fullName, boolean locked, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.locked = locked;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ")" + (locked ? " [LOCKED]" : "");
    }
}
