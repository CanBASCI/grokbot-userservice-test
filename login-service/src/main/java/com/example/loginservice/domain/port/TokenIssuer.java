package com.example.loginservice.domain.port;

import com.example.loginservice.domain.model.AuthenticatedUser;

import java.util.Optional;
import java.util.UUID;

public interface TokenIssuer {

    String issueAccessToken(AuthenticatedUser user);

    long accessTokenTtlSeconds();

    /**
     * Opaque (non-JWT) refresh token. Implementation may bind userId/email for
     * rotation without cross-service DB, while only the SHA-256 hash is stored.
     */
    String generateOpaqueRefreshToken(AuthenticatedUser user);

    Optional<AuthenticatedUser> parseRefreshToken(String rawRefreshToken);

    String hashRefreshToken(String rawRefreshToken);

    UUID newRefreshTokenId();
}
