package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface EndRecording {
    void end(UUID recordingId, UUID userId);   // device path, ownership-checked
    void endAsSystem(UUID recordingId);        // hook path (already authenticated by shared secret)
}
