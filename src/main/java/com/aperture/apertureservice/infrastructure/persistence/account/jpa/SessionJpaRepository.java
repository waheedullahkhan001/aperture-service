package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {
    Optional<SessionJpaEntity> findByRefreshTokenHash(String hash);
    Optional<SessionJpaEntity> findByPreviousTokenHash(String hash);
    List<SessionJpaEntity> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
