package com.example.signupservice.repository;

import com.example.signupservice.domain.port.SignupIdempotencyStore;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SignupIdempotencyStoreAdapter implements SignupIdempotencyStore {

    private final SpringDataSignupIdempotencyJpaRepository jpaRepository;
    private final Clock clock;

    public SignupIdempotencyStoreAdapter(SpringDataSignupIdempotencyJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<Record> find(String idempotencyKey) {
        return jpaRepository.findById(idempotencyKey)
                .map(e -> new Record(e.getIdempotencyKey(), e.getRequestHash(), e.getUserId(), e.getEmail()));
    }

    @Override
    public void save(String idempotencyKey, String requestHash, UUID userId, String email) {
        jpaRepository.save(new SignupIdempotencyEntity(
                idempotencyKey,
                requestHash,
                userId,
                email,
                clock.instant()
        ));
    }
}
