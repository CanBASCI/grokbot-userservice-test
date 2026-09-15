package com.example.loginservice.domain;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.port.CredentialVerifierClient;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import com.example.loginservice.domain.port.TokenIssuer;
import com.example.loginservice.domain.usecase.LoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FakeCredentialVerifier verifier;
    private FakeRefreshRepo refreshRepo;
    private FakeTokenIssuer tokenIssuer;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        verifier = new FakeCredentialVerifier();
        refreshRepo = new FakeRefreshRepo();
        tokenIssuer = new FakeTokenIssuer();
        loginService = new LoginService(
                verifier,
                refreshRepo,
                tokenIssuer,
                3600,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void loginSuccess() {
        verifier.user = new AuthenticatedUser(USER_ID, "user@example.com");
        TokenPair pair = loginService.login("user@example.com", "password123");
        assertEquals("access-token", pair.getAccessToken());
        assertNotNull(pair.getRefreshToken());
        assertEquals(LoginService.TOKEN_TYPE, pair.getTokenType());
        assertEquals(900, pair.getExpiresIn());
        assertEquals(1, refreshRepo.byHash.size());
    }

    @Test
    void loginInvalidCredentials() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> loginService.login("user@example.com", "wrong")
        );
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getCode());
        assertEquals(401, ex.getStatus());
    }

    @Test
    void emailRequired() {
        DomainException ex = assertThrows(DomainException.class, () -> loginService.login(" ", "x"));
        assertEquals(ErrorCode.EMAIL_REQUIRED, ex.getCode());
    }

    private static final class FakeCredentialVerifier implements CredentialVerifierClient {
        AuthenticatedUser user;

        @Override
        public Optional<AuthenticatedUser> verify(String email, String password) {
            return Optional.ofNullable(user);
        }
    }

    private static final class FakeRefreshRepo implements RefreshTokenRepository {
        final Map<String, RefreshTokenRecord> byHash = new HashMap<>();

        @Override
        public RefreshTokenRecord save(RefreshTokenRecord record) {
            byHash.put(record.getTokenHash(), record);
            return record;
        }

        @Override
        public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public void revoke(UUID id) {
            byHash.values().stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .ifPresent(r -> byHash.put(r.getTokenHash(), r.revoked(NOW)));
        }
    }

    private static final class FakeTokenIssuer implements TokenIssuer {
        @Override
        public String issueAccessToken(AuthenticatedUser user) {
            return "access-token";
        }

        @Override
        public long accessTokenTtlSeconds() {
            return 900;
        }

        @Override
        public String generateOpaqueRefreshToken(AuthenticatedUser user) {
            return "opaque." + user.getUserId() + "." + user.getEmail();
        }

        @Override
        public Optional<AuthenticatedUser> parseRefreshToken(String rawRefreshToken) {
            String[] p = rawRefreshToken.split("\\.", 3);
            return Optional.of(new AuthenticatedUser(UUID.fromString(p[1]), p[2]));
        }

        @Override
        public String hashRefreshToken(String rawRefreshToken) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8))
                );
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public UUID newRefreshTokenId() {
            return UUID.randomUUID();
        }
    }
}
