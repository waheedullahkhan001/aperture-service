package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Stub
public class InMemoryRecordingSegments implements RecordingSegments {
    private final List<RecordingSegment> all = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override public int nextNumber(UUID recordingId) {
        return byRecording(recordingId).stream().mapToInt(RecordingSegment::segmentNumber).max().orElse(0) + 1;
    }

    @Override public boolean existsForPath(UUID recordingId, String filePath) {
        return all.stream().anyMatch(s -> s.recordingId().equals(recordingId) && s.filePath().equals(filePath));
    }

    @Override public void save(RecordingSegment s) {
        all.add(new RecordingSegment(seq.incrementAndGet(), s.recordingId(), s.segmentNumber(),
                s.filePath(), s.startTime(), s.endTime(), s.sizeBytes(), s.uploaded(),
                s.source() != null ? s.source() : SegmentSource.STREAMED, s.quality(),
                s.clientClipId()));
    }

    @Override public List<RecordingSegment> byRecording(UUID recordingId) {
        return all.stream().filter(s -> s.recordingId().equals(recordingId))
                .sorted(Comparator.comparingInt(RecordingSegment::segmentNumber)).toList();
    }

    @Override public Optional<RecordingSegment> byNumber(UUID recordingId, int segmentNumber) {
        return byRecording(recordingId).stream().filter(s -> s.segmentNumber() == segmentNumber).findFirst();
    }

    @Override public Optional<RecordingSegment> byClientClipId(UUID recordingId, String clientClipId) {
        return all.stream()
                .filter(s -> s.recordingId().equals(recordingId) && clientClipId.equals(s.clientClipId()))
                .findFirst();
    }

    @Override public void deleteFor(UUID recordingId) {
        all.removeIf(s -> s.recordingId().equals(recordingId));
    }
}
