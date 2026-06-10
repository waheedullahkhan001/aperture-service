package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RecordingJpaRepository extends JpaRepository<RecordingJpaEntity, UUID>, RecordingInsertOperations {

    Page<RecordingJpaEntity> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    Page<RecordingJpaEntity> findByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status, Pageable pageable);

    @Query("select r.id from RecordingJpaEntity r where r.userId = :userId")
    List<UUID> idsByUser(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecordingJpaEntity r where r.id = :id")
    Optional<RecordingJpaEntity> findForUpdate(UUID id);

    @Query("""
            select r from RecordingJpaEntity r
            where r.countdownEndsAt is not null and r.countdownEndsAt <= :now
              and r.alertsDispatchedAt is null
              and r.status in ('PENDING','RECORDING')""")
    List<RecordingJpaEntity> dispatchDue(Instant now);

    @Query("""
            select r from RecordingJpaEntity r
            where r.status = 'PENDING' and r.startedAt < :before""")
    List<RecordingJpaEntity> stalePending(Instant before);

    @Query("""
            select r from RecordingJpaEntity r
            where r.status = 'RECORDING' and r.startedAt < :before
              and not exists (select 1 from SegmentJpaEntity s
                              where s.recordingId = r.id and s.endTime >= :before)""")
    List<RecordingJpaEntity> staleStreaming(Instant before);
}
