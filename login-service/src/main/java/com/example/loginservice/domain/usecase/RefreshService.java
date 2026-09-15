package com.example.loginservice.domain.usecase;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import com.example.loginservice.domain.port.TokenIssuer;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final long refreshTtlSeconds;
    private final Clock clock;

    public RefreshService(
            RefreshTokenRepository refreshTokenRepository,
            TokenIssuer tokenIssuer,
            long refreshTtlSeconds,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.clock = Objects.requireNonNull(clock);
    }

    public TokenPair refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new DomainException(
                    ErrorCode.REFRESH_TOKEN_REQUIRED,
                    400,
                    "Bad Request",
                    "Refresh token is required"
            );
        }

        Instant now = clock.instant();
        String hash = tokenIssuer.hashRefreshToken(rawRefreshToken);
        RefreshTokenRecord existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(RefreshService::invalidRefresh);

        if (!existing.isActive(now)) {
            throw invalidRefresh();
        }

        AuthenticatedUser user = tokenIssuer.parseRefreshToken(rawRefreshToken)
                .filter(u -> u.getUserId().equals(existing.getUserId()))
                .orElseThrow(RefreshService::invalidRefresh);

        refreshTokenRepository.revoke(existing.getId());

        String access = tokenIssuer.issueAccessToken(user);
        String newRaw = tokenIssuer.generateOpaqueRefreshToken(user);
        String newHash = tokenIssuer.hashRefreshToken(newRaw);

        RefreshTokenRecord next = new RefreshTokenRecord(
                tokenIssuer.newRefreshTokenId(),
                user.getUserId(),
                newHash,
                now.plusSeconds(refreshTtlSeconds),
                null,
                now
        );
        refreshTokenRepository.save(next);

        return new TokenPair(access, newRaw, "Bearer", tokenIssuer.accessTokenTtlSeconds());
    }

    private static DomainException invalidRefresh() {
        return new DomainException(
                ErrorCode.INVALID_REFRESH_TOKEN,
                401,
                "Unauthorized",
                "Invalid refresh token"
        );
    }
}
