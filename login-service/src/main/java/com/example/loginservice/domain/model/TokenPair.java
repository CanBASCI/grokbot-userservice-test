package com.example.loginservice.domain.model;

import java.util.Objects;

public final class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;

    public TokenPair(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        this.accessToken = Objects.requireNonNull(accessToken);
        this.refreshToken = Objects.requireNonNull(refreshToken);
        this.tokenType = Objects.requireNonNull(tokenType);
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
