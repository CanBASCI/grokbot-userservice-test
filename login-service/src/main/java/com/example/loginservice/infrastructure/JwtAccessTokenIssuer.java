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
import java.util.Optional;
import java.util.UUID;

public final class JwtAccessTokenIssuer implements TokenIssuer {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

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
    public String generateOpaqueRefreshToken(AuthenticatedUser user) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        // Opaque non-JWT string: random + bound userId/email so refresh can rebuild
        // access claims without shared DB / user lookup. Only SHA-256 is persisted.
        return URL_ENCODER.encodeToString(random)
                + "."
                + URL_ENCODER.encodeToString(user.getUserId().toString().getBytes(StandardCharsets.UTF_8))
                + "."
                + URL_ENCODER.encodeToString(user.getEmail().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<AuthenticatedUser> parseRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return Optional.empty();
        }
        String[] parts = rawRefreshToken.split("\\.", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            String email = new String(URL_DECODER.decode(parts[2]), StandardCharsets.UTF_8);
            if (email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, email));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
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
