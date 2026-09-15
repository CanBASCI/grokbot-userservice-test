package com.example.loginservice.repository;

import com.example.loginservice.domain.model.RefreshTokenRecord;
import com.example.loginservice.domain.port.RefreshTokenRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenJpaRepository jpaRepository;
    private final Clock clock;

    public RefreshTokenRepositoryAdapter(SpringDataRefreshTokenJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public RefreshTokenRecord save(RefreshTokenRecord record) {
        RefreshTokenEntity entity = new RefreshTokenEntity(
                record.getId(),
                record.getUserId(),
                record.getEmail(),
                record.getTokenHash(),
                record.getExpiresAt(),
                record.getRevokedAt(),
                record.getCreatedAt()
        );
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean claimActive(String tokenHash, Instant now) {
        return jpaRepository.claimActive(tokenHash, now) == 1;
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        jpaRepository.revokeAllForUser(userId, clock.instant());
    }

    @Override
    @Transactional
    public void revoke(UUID id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setRevokedAt(clock.instant());
            jpaRepository.save(entity);
        });
    }

    private static RefreshTokenRecord toDomain(RefreshTokenEntity entity) {
        return new RefreshTokenRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt()
        );
    }
}
