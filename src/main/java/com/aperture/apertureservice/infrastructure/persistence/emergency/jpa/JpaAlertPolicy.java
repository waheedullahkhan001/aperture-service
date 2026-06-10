package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.recording.spi.AlertPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAlertPolicy implements AlertPolicy {

    private final ContactJpaRepository contacts;
    private final AlertConfigurationJpaRepository configs;

    JpaAlertPolicy(ContactJpaRepository contacts, AlertConfigurationJpaRepository configs) {
        this.contacts = contacts;
        this.configs = configs;
    }

    @Override
    public Optional<Duration> activeCountdownFor(UUID userId) {
        if (contacts.countByUserId(userId) == 0) {
            return Optional.empty();
        }
        int seconds = configs.findById(userId)
                .map(e -> e.toDomain().countdownDurationSeconds())
                .orElse(30);
        return Optional.of(Duration.ofSeconds(seconds));
    }
}
