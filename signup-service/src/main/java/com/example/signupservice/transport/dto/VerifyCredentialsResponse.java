package com.example.signupservice.transport.dto;

import java.util.UUID;

public class VerifyCredentialsResponse {

    private UUID userId;
    private String email;

    public VerifyCredentialsResponse() {
    }

    public VerifyCredentialsResponse(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
