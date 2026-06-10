package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;

import java.util.Optional;
import java.util.UUID;

public interface ListRecordings {
    PageOf<Recording> list(UUID userId, Optional<RecordingStatus> status, int page, int size);
}
