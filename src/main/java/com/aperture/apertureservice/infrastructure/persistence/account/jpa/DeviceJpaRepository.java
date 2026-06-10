package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DeviceJpaRepository extends JpaRepository<DeviceJpaEntity, UUID> {
    Optional<DeviceJpaEntity> findByTokenHash(String tokenHash);
    List<DeviceJpaEntity> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
