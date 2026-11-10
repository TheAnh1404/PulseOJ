package com.vku.pulseoj.common.dto;

import java.io.Serializable;

/**
 * Authentication request payload sent from Contestant Client to Master.
 */
public class LoginRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String clientHost;

    public LoginRequestDTO() {}

    public LoginRequestDTO(String username, String password, String clientHost) {
        this.username = username;
        this.password = password;
        this.clientHost = clientHost;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientHost() {
        return clientHost;
    }

    public void setClientHost(String clientHost) {
        this.clientHost = clientHost;
    }
}
