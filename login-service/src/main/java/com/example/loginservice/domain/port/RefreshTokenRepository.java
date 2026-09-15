package com.example.loginservice.domain.port;

import com.example.loginservice.domain.model.RefreshTokenRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshTokenRecord save(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    /**
     * Atomically claim an active token: set revoked_at where hash matches and still active.
     * @return true if exactly one row was claimed
     */
    boolean claimActive(String tokenHash, Instant now);

    void revokeAllForUser(UUID userId);

    void revoke(UUID id);
}
