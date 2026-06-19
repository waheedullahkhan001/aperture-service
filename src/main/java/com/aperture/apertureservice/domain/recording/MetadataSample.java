package com.aperture.apertureservice.domain.recording;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MetadataSample(Long id, UUID recordingId, BigDecimal latitude, BigDecimal longitude,
                             Instant clientTimestamp, Instant serverReceivedAt, String deviceInfo,
                             Double horizontalAccuracyM, Double speedMps, Double bearingDeg,
                             Double altitudeM, Integer batteryPercent) {}
