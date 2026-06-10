package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface DeleteRecording {
    void delete(UUID userId, UUID recordingId);
}
