package com.aperture.apertureservice.domain.recording.api;

import com.aperture.apertureservice.domain.recording.SegmentDownload;

import java.util.UUID;

/**
 * Returns a streaming-ready download for a segment belonging to the recording identified by
 * {@code recordingId}, after verifying that {@code viewSecret} is valid. No JWT / userId required.
 */
public interface StreamWatchSegment {
    SegmentDownload stream(UUID recordingId, int segmentNumber, String viewSecret);
}
