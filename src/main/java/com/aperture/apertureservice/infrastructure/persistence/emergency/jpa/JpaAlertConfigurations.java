package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAlertConfigurations implements AlertConfigurations {

    private final AlertConfigurationJpaRepository repo;

    JpaAlertConfigurations(AlertConfigurationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<AlertConfiguration> byUser(UUID userId) {
        return repo.findById(userId).map(AlertConfigurationJpaEntity::toDomain);
    }

    @Override
    public void save(AlertConfiguration c) {
        repo.save(AlertConfigurationJpaEntity.from(c));
    }
}
