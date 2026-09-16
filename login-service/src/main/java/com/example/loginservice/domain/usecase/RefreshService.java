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
    private final LoginService loginService;
    private final Clock clock;

    public RefreshService(
            RefreshTokenRepository refreshTokenRepository,
            TokenIssuer tokenIssuer,
            LoginService loginService,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.loginService = Objects.requireNonNull(loginService);
        this.clock = Objects.requireNonNull(clock);
    }

    public TokenPair refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new DomainException(ErrorCode.REFRESH_TOKEN_REQUIRED, "Refresh token is required");
        }

        Instant now = clock.instant();
        String hash = tokenIssuer.hashRefreshToken(rawRefreshToken);
        RefreshTokenRecord existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(RefreshService::invalidRefresh);

        if (existing.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(existing.getUserId());
            throw invalidRefresh();
        }

        if (!existing.isActive(now)) {
            throw invalidRefresh();
        }

        if (!refreshTokenRepository.claimActive(hash, now)) {
            throw invalidRefresh();
        }

        AuthenticatedUser user = new AuthenticatedUser(existing.getUserId(), existing.getEmail());
        return loginService.issueTokens(user);
    }

    private static DomainException invalidRefresh() {
        return new DomainException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token");
    }
}
