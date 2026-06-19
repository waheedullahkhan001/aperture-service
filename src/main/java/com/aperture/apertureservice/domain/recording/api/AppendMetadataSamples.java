package com.aperture.apertureservice.domain.recording.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppendMetadataSamples {
    int append(UUID recordingId, UUID userId, List<NewSample> samples);

    record NewSample(BigDecimal latitude, BigDecimal longitude, Instant clientTimestamp, String deviceInfo,
                     Double horizontalAccuracyM, Double speedMps, Double bearingDeg,
                     Double altitudeM, Integer batteryPercent) {}
}
