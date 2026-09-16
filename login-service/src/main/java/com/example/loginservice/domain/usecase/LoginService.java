package com.example.loginservice.domain.usecase;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.port.CredentialVerifierClient;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import com.example.loginservice.domain.port.TokenIssuer;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class LoginService {

    public static final String TOKEN_TYPE = "Bearer";

    private final CredentialVerifierClient credentialVerifierClient;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final long refreshTtlSeconds;
    private final Clock clock;

    public LoginService(
            CredentialVerifierClient credentialVerifierClient,
            RefreshTokenRepository refreshTokenRepository,
            TokenIssuer tokenIssuer,
            long refreshTtlSeconds,
            Clock clock
    ) {
        this.credentialVerifierClient = Objects.requireNonNull(credentialVerifierClient);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.clock = Objects.requireNonNull(clock);
    }

    public TokenPair login(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new DomainException(ErrorCode.EMAIL_REQUIRED, "Email is required");
        }
        if (password == null || password.isBlank()) {
            throw new DomainException(ErrorCode.PASSWORD_REQUIRED, "Password is required");
        }

        AuthenticatedUser user = credentialVerifierClient.verify(email, password)
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));

        return issueTokens(user);
    }

    public TokenPair issueTokens(AuthenticatedUser user) {
        Instant now = clock.instant();
        String access = tokenIssuer.issueAccessToken(user);
        String rawRefresh = tokenIssuer.generateOpaqueRefreshToken();
        String hash = tokenIssuer.hashRefreshToken(rawRefresh);

        RefreshTokenRecord record = new RefreshTokenRecord(
                tokenIssuer.newRefreshTokenId(),
                user.getUserId(),
                user.getEmail(),
                hash,
                now.plusSeconds(refreshTtlSeconds),
                null,
                now
        );
        refreshTokenRepository.save(record);

        return new TokenPair(access, rawRefresh, TOKEN_TYPE, tokenIssuer.accessTokenTtlSeconds());
    }
}
