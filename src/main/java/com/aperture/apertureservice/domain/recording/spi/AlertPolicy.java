package com.aperture.apertureservice.domain.recording.spi;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface AlertPolicy {
    /** Present iff the user has at least one emergency contact; value = configured countdown. */
    Optional<Duration> activeCountdownFor(UUID userId);
}
