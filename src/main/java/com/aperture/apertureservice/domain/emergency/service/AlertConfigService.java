package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.UpdateAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;
import jakarta.transaction.Transactional;

import java.util.UUID;

@DomainService
public class AlertConfigService implements GetAlertConfiguration, UpdateAlertConfiguration {

    private final AlertConfigurations configs;

    public AlertConfigService(AlertConfigurations configs) {
        this.configs = configs;
    }

    @Override
    public AlertConfiguration get(UUID userId) {
        return configs.byUser(userId).orElse(AlertConfiguration.defaults(userId));
    }

    @Override
    @Transactional
    public AlertConfiguration update(UUID userId, int countdownDurationSeconds, String messageTemplate) {
        if (countdownDurationSeconds < 0 || countdownDurationSeconds > 3600) {
            throw new BadRequest("COUNTDOWN_RANGE", "Countdown must be between 0 and 3600 seconds");
        }
        if (messageTemplate == null || messageTemplate.isBlank()) {
            throw new BadRequest("TEMPLATE_REQUIRED", "Message template is required");
        }
        AlertConfiguration updated = new AlertConfiguration(userId, countdownDurationSeconds, messageTemplate.trim());
        configs.save(updated);
        return updated;
    }
}
