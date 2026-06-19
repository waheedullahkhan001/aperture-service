package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.RecordingSegment;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

public interface UploadClip {
    RecordingSegment upload(UUID recordingId, UUID userId, InputStream data, String filename,
                            long sizeHint, Instant startTime, Instant endTime,
                            String quality, String clipId);
}
