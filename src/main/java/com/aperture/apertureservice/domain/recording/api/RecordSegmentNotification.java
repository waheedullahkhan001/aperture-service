package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface RecordSegmentNotification {
    /** Hook path (system-authenticated). Unknown recording ids are ignored (logged by caller). */
    void segmentCompleted(UUID recordingId, String segmentPath, double durationSeconds);
}
