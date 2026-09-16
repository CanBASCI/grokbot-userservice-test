package com.example.signupservice.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signup_idempotency")
public class SignupIdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", length = 320, nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SignupIdempotencyEntity() {
    }

    public SignupIdempotencyEntity(
            String idempotencyKey,
            String requestHash,
            UUID userId,
            String email,
            Instant createdAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.userId = userId;
        this.email = email;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
