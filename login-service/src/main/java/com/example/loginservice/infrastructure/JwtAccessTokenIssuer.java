package com.example.loginservice.infrastructure;

import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.port.TokenIssuer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class JwtAccessTokenIssuer implements TokenIssuer {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKey secretKey;
    private final long accessTtlSeconds;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtAccessTokenIssuer(String secret, long accessTtlSeconds, Clock clock) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(accessTtlSeconds);
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public long accessTokenTtlSeconds() {
        return accessTtlSeconds;
    }

    @Override
    public String generateOpaqueRefreshToken() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return URL_ENCODER.encodeToString(random);
    }

    @Override
    public String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public UUID newRefreshTokenId() {
        return UUID.randomUUID();
    }
}
