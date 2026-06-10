package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.RecordingDetail;

import java.util.UUID;

public interface GetRecording {
    RecordingDetail get(UUID userId, UUID recordingId);
}
