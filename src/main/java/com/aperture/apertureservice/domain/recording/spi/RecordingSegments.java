package com.aperture.apertureservice.domain.recording.spi;

import com.aperture.apertureservice.domain.recording.RecordingSegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingSegments {
    int nextNumber(UUID recordingId);
    boolean existsForPath(UUID recordingId, String filePath);
    void save(RecordingSegment segment);
    List<RecordingSegment> byRecording(UUID recordingId);
    Optional<RecordingSegment> byNumber(UUID recordingId, int segmentNumber);
    Optional<RecordingSegment> byClientClipId(UUID recordingId, String clientClipId);
    void deleteFor(UUID recordingId);
}
