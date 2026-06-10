package com.aperture.apertureservice.domain.emergency.api;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;

import java.util.UUID;

public interface GetAlertConfiguration {
    AlertConfiguration get(UUID userId);
}
