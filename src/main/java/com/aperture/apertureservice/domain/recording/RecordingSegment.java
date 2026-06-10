package com.aperture.apertureservice.domain.recording;

import java.time.Instant;
import java.util.UUID;

public record RecordingSegment(Long id, UUID recordingId, int segmentNumber, String filePath,
                               Instant startTime, Instant endTime, long sizeBytes, boolean uploaded) {}
