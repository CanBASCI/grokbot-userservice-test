package com.example.loginservice.domain.port;

import com.example.loginservice.domain.model.AuthenticatedUser;

import java.util.UUID;

public interface TokenIssuer {

    String issueAccessToken(AuthenticatedUser user);

    long accessTokenTtlSeconds();

    /** Random opaque refresh token (not JWT); identity lives in the refresh_tokens row. */
    String generateOpaqueRefreshToken();

    String hashRefreshToken(String rawRefreshToken);

    UUID newRefreshTokenId();
}
