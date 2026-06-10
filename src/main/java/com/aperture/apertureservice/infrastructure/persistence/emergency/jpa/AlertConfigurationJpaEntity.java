package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "alert_configurations")
class AlertConfigurationJpaEntity {

    @Id
    @Column(name = "user_id")
    UUID userId;

    @Column(name = "countdown_duration_seconds", nullable = false)
    int countdownDurationSeconds;

    @Column(name = "message_template", nullable = false, length = 4000)
    String messageTemplate;

    protected AlertConfigurationJpaEntity() {}

    static AlertConfigurationJpaEntity from(AlertConfiguration c) {
        AlertConfigurationJpaEntity e = new AlertConfigurationJpaEntity();
        e.userId = c.userId();
        e.countdownDurationSeconds = c.countdownDurationSeconds();
        e.messageTemplate = c.messageTemplate();
        return e;
    }

    AlertConfiguration toDomain() {
        return new AlertConfiguration(userId, countdownDurationSeconds, messageTemplate);
    }
}
