package com.example.loginservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class AuthenticatedUser {

    private final UUID userId;
    private final String email;

    public AuthenticatedUser(UUID userId, String email) {
        this.userId = Objects.requireNonNull(userId);
        this.email = Objects.requireNonNull(email);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
