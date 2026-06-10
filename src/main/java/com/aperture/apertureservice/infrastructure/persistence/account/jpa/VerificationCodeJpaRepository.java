package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationCodeJpaRepository extends JpaRepository<VerificationCodeJpaEntity, VerificationCodeId> {
    void deleteByUserId(UUID userId);
}
