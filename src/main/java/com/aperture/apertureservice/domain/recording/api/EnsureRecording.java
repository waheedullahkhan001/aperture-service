package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.EnsureResult;

import java.time.Instant;
import java.util.UUID;

public interface EnsureRecording {
    /** Idempotent: creates the recording if absent, returns the current row otherwise.
     *  clientStartedAt may be null (hook path). */
    EnsureResult ensure(UUID recordingId, UUID userId, Instant clientStartedAt);
}
