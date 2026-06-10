package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface DispatchAttemptJpaRepository extends JpaRepository<DispatchAttemptJpaEntity, Long> {
    List<DispatchAttemptJpaEntity> findBySuccessFalseAndAttemptedAtGreaterThanEqual(Instant since);
    int countByRecordingIdAndContactId(UUID recordingId, Long contactId);
    boolean existsByRecordingIdAndContactIdAndSuccessTrue(UUID recordingId, Long contactId);
}
