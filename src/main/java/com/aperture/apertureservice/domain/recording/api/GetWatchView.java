package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.WatchView;

import java.util.UUID;

public interface GetWatchView {
    WatchView watch(UUID recordingId, String viewSecret);
}
