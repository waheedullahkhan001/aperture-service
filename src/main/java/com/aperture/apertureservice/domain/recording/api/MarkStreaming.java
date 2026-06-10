package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface MarkStreaming {
    void markStreaming(UUID recordingId, UUID userId);
}
