package com.example.loginservice.domain.port;

import com.example.loginservice.domain.model.RefreshTokenRecord;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshTokenRecord save(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    void revoke(UUID id);
}
