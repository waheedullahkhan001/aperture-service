package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.spi.AlertPolicy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Stub
public class FixedAlertPolicy implements AlertPolicy {
    private Optional<Duration> countdown;
    public FixedAlertPolicy(Duration countdown) { this.countdown = Optional.ofNullable(countdown); }
    public void set(Duration countdown) { this.countdown = Optional.ofNullable(countdown); }
    @Override public Optional<Duration> activeCountdownFor(UUID userId) { return countdown; }
}
