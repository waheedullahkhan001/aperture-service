package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.SegmentDownload;

import java.util.UUID;

public interface DownloadSegment {
    SegmentDownload download(UUID userId, UUID recordingId, int segmentNumber);
}
