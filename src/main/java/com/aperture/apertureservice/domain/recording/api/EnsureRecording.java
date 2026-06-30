package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.EnsureResult;

import java.time.Instant;
import java.util.UUID;

public interface EnsureRecording {
    /** Idempotent: creates the recording if absent, returns the current row otherwise.
     *  clientStartedAt may be null (hook path). deviceId may be null (clip-upload path or unknown). */
    EnsureResult ensure(UUID recordingId, UUID userId, Instant clientStartedAt, UUID deviceId);
}
