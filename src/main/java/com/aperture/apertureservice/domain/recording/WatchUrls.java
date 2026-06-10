package com.aperture.apertureservice.domain.recording;

import java.util.UUID;

public final class WatchUrls {
    private WatchUrls() {}

    public static String of(String publicOrigin, UUID recordingId, String viewSecret) {
        return publicOrigin + "/watch/" + recordingId + "?t=" + viewSecret;
    }
}
