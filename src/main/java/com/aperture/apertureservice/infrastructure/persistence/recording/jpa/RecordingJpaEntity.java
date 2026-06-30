package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recordings", indexes = {
        @Index(name = "idx_recordings_user_status", columnList = "user_id,status"),
        @Index(name = "idx_recordings_countdown", columnList = "countdown_ends_at")})
class RecordingJpaEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false, length = 16)
    String status;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "ended_at")
    Instant endedAt;

    @Column(name = "view_secret", nullable = false, unique = true)
    String viewSecret;

    @Column(name = "countdown_ends_at")
    Instant countdownEndsAt;

    @Column(name = "alerts_dispatched_at")
    Instant alertsDispatchedAt;

    @Column(name = "view_revoked", nullable = false)
    boolean viewRevoked;

    @Column(name = "device_id")
    UUID deviceId;

    protected RecordingJpaEntity() {}

    static RecordingJpaEntity from(Recording r) {
        RecordingJpaEntity e = new RecordingJpaEntity();
        e.id = r.id();
        e.userId = r.userId();
        e.status = r.status().name();
        e.startedAt = r.startedAt();
        e.endedAt = r.endedAt();
        e.viewSecret = r.viewSecret();
        e.countdownEndsAt = r.countdownEndsAt();
        e.alertsDispatchedAt = r.alertsDispatchedAt();
        e.viewRevoked = r.viewRevoked();
        e.deviceId = r.deviceId();
        return e;
    }

    Recording toDomain() {
        return new Recording(id, userId, RecordingStatus.valueOf(status), startedAt, endedAt,
                viewSecret, countdownEndsAt, alertsDispatchedAt, viewRevoked, deviceId);
    }
}
