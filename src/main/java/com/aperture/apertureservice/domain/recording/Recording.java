package com.aperture.apertureservice.domain.recording;

import java.time.Instant;
import java.util.UUID;

public record Recording(UUID id, UUID userId, RecordingStatus status, Instant startedAt,
                        Instant endedAt, String viewSecret, Instant countdownEndsAt,
                        Instant alertsDispatchedAt, boolean viewRevoked) {

    public boolean live() {
        return status == RecordingStatus.PENDING || status == RecordingStatus.RECORDING;
    }

    public Recording streaming() {
        return new Recording(id, userId, RecordingStatus.RECORDING, startedAt, endedAt,
                viewSecret, countdownEndsAt, alertsDispatchedAt, viewRevoked);
    }

    public Recording ended(Instant at) {
        return new Recording(id, userId, RecordingStatus.ENDED, startedAt, at,
                viewSecret, countdownEndsAt, alertsDispatchedAt, viewRevoked);
    }

    public Recording failed(Instant at) {
        return new Recording(id, userId, RecordingStatus.FAILED, startedAt, at,
                viewSecret, countdownEndsAt, alertsDispatchedAt, viewRevoked);
    }

    public Recording dispatched(Instant at) {
        return new Recording(id, userId, status, startedAt, endedAt,
                viewSecret, countdownEndsAt, at, viewRevoked);
    }

    /** Disarms the alert countdown; recording continues, alerts will never fire. */
    public Recording disarmed() {
        return new Recording(id, userId, status, startedAt, endedAt, viewSecret, null, alertsDispatchedAt, viewRevoked);
    }

    public Recording revokedView() {
        return new Recording(id, userId, status, startedAt, endedAt, viewSecret, countdownEndsAt, alertsDispatchedAt, true);
    }
}
