package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface VerificationCodeJpaRepository extends JpaRepository<VerificationCodeJpaEntity, VerificationCodeId> {
}
