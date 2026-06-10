package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface AuthorizeView {
    void authorizeView(UUID recordingId, String viewSecret);
}
