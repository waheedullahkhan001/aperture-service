package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface AuthorizePublish {
    void authorizePublish(String deviceToken, UUID recordingId);
}
