package com.example.loginservice.infrastructure;

import com.example.loginservice.domain.model.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAccessTokenIssuerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuesAccessClaimsOnlyAndOpaqueRefreshHasNoIdentity() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(SECRET, 900, clock);
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com");

        String jwt = issuer.issueAccessToken(user);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .clock(() -> Date.from(now))
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("user@example.com", claims.get("email", String.class));
        assertEquals("access", claims.get("typ", String.class));

        String refresh = issuer.generateOpaqueRefreshToken();
        assertFalse(refresh.contains("@"));
        assertFalse(refresh.contains(userId.toString()));
        assertFalse(refresh.contains("."));
        assertNotEquals(jwt, refresh);
        assertEquals(64, issuer.hashRefreshToken(refresh).length());
        assertTrue(refresh.length() >= 40);
    }
}
