package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ContactJpaRepository extends JpaRepository<ContactJpaEntity, Long> {
    List<ContactJpaEntity> findByUserIdOrderById(UUID userId);
    int countByUserId(UUID userId);
    boolean existsByUserIdAndEmail(UUID userId, String email);
    void deleteByUserId(UUID userId);
}
