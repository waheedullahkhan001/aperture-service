package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaRecordingSegments implements RecordingSegments {

    private final SegmentJpaRepository repo;

    JpaRecordingSegments(SegmentJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public int nextNumber(UUID recordingId) {
        return repo.maxNumber(recordingId) + 1;
    }

    @Override
    public boolean existsForPath(UUID recordingId, String filePath) {
        return repo.existsByRecordingIdAndFilePath(recordingId, filePath);
    }

    @Override
    public void save(RecordingSegment s) {
        repo.save(SegmentJpaEntity.from(s));
    }

    @Override
    public List<RecordingSegment> byRecording(UUID recordingId) {
        return repo.findByRecordingIdOrderBySegmentNumber(recordingId).stream()
                .map(SegmentJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<RecordingSegment> byNumber(UUID recordingId, int segmentNumber) {
        return repo.findByRecordingIdAndSegmentNumber(recordingId, segmentNumber)
                .map(SegmentJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteFor(UUID recordingId) {
        repo.deleteByRecordingId(recordingId);
    }
}
