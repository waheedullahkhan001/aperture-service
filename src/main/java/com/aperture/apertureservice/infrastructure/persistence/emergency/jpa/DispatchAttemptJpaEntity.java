package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_dispatch_attempts")
class DispatchAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "recording_id")
    UUID recordingId;

    @Column(name = "contact_id")
    Long contactId;

    @Column(name = "attempted_at", nullable = false)
    Instant attemptedAt;

    @Column(nullable = false)
    boolean success;

    @Column(name = "error_message", length = 2000)
    String errorMessage;

    protected DispatchAttemptJpaEntity() {}

    static DispatchAttemptJpaEntity from(AlertDispatchAttempt a) {
        DispatchAttemptJpaEntity e = new DispatchAttemptJpaEntity();
        e.id = a.id();
        e.recordingId = a.recordingId();
        e.contactId = a.contactId();
        e.attemptedAt = a.attemptedAt();
        e.success = a.success();
        e.errorMessage = a.errorMessage();
        return e;
    }

    AlertDispatchAttempt toDomain() {
        return new AlertDispatchAttempt(id, recordingId, contactId, attemptedAt, success, errorMessage);
    }
}
