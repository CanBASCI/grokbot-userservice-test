package com.example.loginservice.domain;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import com.example.loginservice.domain.port.TokenIssuer;
import com.example.loginservice.domain.usecase.RefreshService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FakeRefreshRepo refreshRepo;
    private FakeTokenIssuer tokenIssuer;
    private RefreshService refreshService;
    private String rawToken;

    @BeforeEach
    void setUp() {
        refreshRepo = new FakeRefreshRepo();
        tokenIssuer = new FakeTokenIssuer();
        refreshService = new RefreshService(
                refreshRepo,
                tokenIssuer,
                3600,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "user@example.com");
        rawToken = tokenIssuer.generateOpaqueRefreshToken(user);
        refreshRepo.save(new RefreshTokenRecord(
                UUID.randomUUID(),
                USER_ID,
                tokenIssuer.hashRefreshToken(rawToken),
                NOW.plusSeconds(3600),
                null,
                NOW
        ));
    }

    @Test
    void refreshRotatesToken() {
        TokenPair pair = refreshService.refresh(rawToken);
        assertTrue(pair.getAccessToken().startsWith("access-"));
        assertNotEquals(rawToken, pair.getRefreshToken());
        assertTrue(refreshRepo.byHash.values().stream().anyMatch(r -> r.getRevokedAt() != null));
        assertEquals(2, refreshRepo.byHash.size());
    }

    @Test
    void refreshInvalidUnknown() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> refreshService.refresh("unknown.token.value")
        );
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getCode());
        assertEquals(401, ex.getStatus());
    }

    @Test
    void refreshRevokedRejected() {
        refreshService.refresh(rawToken);
        DomainException ex = assertThrows(DomainException.class, () -> refreshService.refresh(rawToken));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getCode());
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
            byHash.replaceAll((k, v) -> v.getId().equals(id) ? v.revoked(NOW) : v);
        }
    }

    private static final class FakeTokenIssuer implements TokenIssuer {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public String issueAccessToken(AuthenticatedUser user) {
            return "access-" + counter.incrementAndGet();
        }

        @Override
        public long accessTokenTtlSeconds() {
            return 900;
        }

        @Override
        public String generateOpaqueRefreshToken(AuthenticatedUser user) {
            return "opaque-" + counter.incrementAndGet() + "." + user.getUserId() + "." + user.getEmail();
        }

        @Override
        public Optional<AuthenticatedUser> parseRefreshToken(String rawRefreshToken) {
            String[] p = rawRefreshToken.split("\\.", 3);
            if (p.length != 3) {
                return Optional.empty();
            }
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
