package com.example.signupservice.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface SignupIdempotencyStore {

    Optional<Record> find(String idempotencyKey);

    void save(String idempotencyKey, String requestHash, UUID userId, String email);

    record Record(String idempotencyKey, String requestHash, UUID userId, String email) {
    }
}
