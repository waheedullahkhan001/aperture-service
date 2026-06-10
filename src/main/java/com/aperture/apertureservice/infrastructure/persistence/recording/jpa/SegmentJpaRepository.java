package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SegmentJpaRepository extends JpaRepository<SegmentJpaEntity, Long> {

    @Query("select coalesce(max(s.segmentNumber), 0) from SegmentJpaEntity s where s.recordingId = :recordingId")
    int maxNumber(UUID recordingId);

    boolean existsByRecordingIdAndFilePath(UUID recordingId, String filePath);

    List<SegmentJpaEntity> findByRecordingIdOrderBySegmentNumber(UUID recordingId);

    Optional<SegmentJpaEntity> findByRecordingIdAndSegmentNumber(UUID recordingId, int segmentNumber);

    void deleteByRecordingId(UUID recordingId);
}
