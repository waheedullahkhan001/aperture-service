package com.aperture.apertureservice.domain.emergency.api;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;

import java.util.UUID;

public interface UpdateAlertConfiguration {
    AlertConfiguration update(UUID userId, int countdownDurationSeconds, String messageTemplate);
}
