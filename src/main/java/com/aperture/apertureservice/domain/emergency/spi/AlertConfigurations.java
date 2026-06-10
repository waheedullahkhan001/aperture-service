package com.aperture.apertureservice.domain.emergency.spi;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;

import java.util.Optional;
import java.util.UUID;

public interface AlertConfigurations {
    Optional<AlertConfiguration> byUser(UUID userId);
    void save(AlertConfiguration configuration);
}
