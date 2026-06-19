package com.aperture.apertureservice.domain.recording;

import java.time.Instant;

/** Public-facing segment descriptor returned to watch-link holders — no filePath, no db id. */
public record WatchSegment(int segmentNumber, Instant startTime, Instant endTime,
                           String source, String quality, long sizeBytes) {}
