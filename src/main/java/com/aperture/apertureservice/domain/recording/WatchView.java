package com.aperture.apertureservice.domain.recording;

import java.time.Instant;
import java.util.Optional;

public record WatchView(String ownerName, Instant startedAt, RecordingStatus status,
                        Optional<MetadataSample> latestSample, String hlsUrl, String webrtcUrl) {}
