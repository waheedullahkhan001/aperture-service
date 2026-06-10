package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MetadataSampleJpaRepository extends JpaRepository<MetadataSampleJpaEntity, Long> {

    Optional<MetadataSampleJpaEntity> findFirstByRecordingIdOrderByClientTimestampDesc(UUID recordingId);

    List<MetadataSampleJpaEntity> findByRecordingIdOrderByClientTimestampDesc(UUID recordingId, Pageable pageable);

    void deleteByRecordingId(UUID recordingId);
}
