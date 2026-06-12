package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.CancelResult;

import java.util.UUID;

public interface CancelAlerts {
    /** Idempotent. Returns cancelled=false only when alerts were already dispatched (irreversible). */
    CancelResult cancelAlerts(UUID recordingId, UUID userId);
}
