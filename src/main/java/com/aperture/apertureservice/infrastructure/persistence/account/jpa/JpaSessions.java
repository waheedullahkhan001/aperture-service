package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaSessions implements Sessions {

    private final SessionJpaRepository repo;

    JpaSessions(SessionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<Session> byId(UUID id) {
        return repo.findById(id).map(SessionJpaEntity::toDomain);
    }

    @Override
    public Optional<Session> byRefreshTokenHash(String h) {
        return repo.findByRefreshTokenHash(h).map(SessionJpaEntity::toDomain);
    }

    @Override
    public Optional<Session> byPreviousTokenHash(String h) {
        return repo.findByPreviousTokenHash(h).map(SessionJpaEntity::toDomain);
    }

    @Override
    public List<Session> byUser(UUID userId) {
        return repo.findByUserId(userId).stream().map(SessionJpaEntity::toDomain).toList();
    }

    @Override
    public void save(Session s) {
        repo.save(SessionJpaEntity.from(s));
    }

    @Override
    public void delete(UUID sessionId) {
        repo.deleteById(sessionId);
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        repo.deleteByUserId(userId);
    }
}
