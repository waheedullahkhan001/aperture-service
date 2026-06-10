package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import com.aperture.apertureservice.domain.emergency.spi.AlertDispatchAttempts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaAlertDispatchAttempts implements AlertDispatchAttempts {

    private final DispatchAttemptJpaRepository repo;

    JpaAlertDispatchAttempts(DispatchAttemptJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public AlertDispatchAttempt record(AlertDispatchAttempt a) {
        return repo.save(DispatchAttemptJpaEntity.from(a)).toDomain();
    }

    @Override
    public List<AlertDispatchAttempt> failedSince(Instant since) {
        return repo.findBySuccessFalseAndAttemptedAtGreaterThanEqual(since).stream()
                .map(DispatchAttemptJpaEntity::toDomain).toList();
    }

    @Override
    public int countFor(UUID recordingId, Long contactId) {
        return repo.countByRecordingIdAndContactId(recordingId, contactId);
    }

    @Override
    public boolean anySuccess(UUID recordingId, Long contactId) {
        return repo.existsByRecordingIdAndContactIdAndSuccessTrue(recordingId, contactId);
    }
}
