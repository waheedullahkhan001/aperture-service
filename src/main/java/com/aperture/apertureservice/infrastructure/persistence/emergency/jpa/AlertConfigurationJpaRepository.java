package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AlertConfigurationJpaRepository extends JpaRepository<AlertConfigurationJpaEntity, UUID> {
}
