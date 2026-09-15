package com.example.loginservice.repository;

import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
@EntityScan(basePackages = "com.example.loginservice.repository")
@EnableJpaRepositories(basePackages = "com.example.loginservice.repository")
@Import({RefreshTokenRepositoryAdapter.class, RefreshTokenRepositoryAdapterTest.ClockConfig.class})
class RefreshTokenRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private RefreshTokenRepository repository;

    @Test
    void saveFindAndRevoke() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshTokenRecord saved = repository.save(new RefreshTokenRecord(
                id,
                userId,
                "abc123hash",
                NOW.plusSeconds(60),
                null,
                NOW
        ));
        assertEquals(id, saved.getId());
        assertTrue(repository.findByTokenHash("abc123hash").isPresent());
        repository.revoke(id);
        assertNotNull(repository.findByTokenHash("abc123hash").orElseThrow().getRevokedAt());
    }

    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
