package com.aperture.apertureservice.domain.recording.api;

import java.util.UUID;

public interface RevokeWatchLink {
    void revoke(UUID userId, UUID recordingId);
}
