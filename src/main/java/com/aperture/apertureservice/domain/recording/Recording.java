package com.aperture.apertureservice.domain.recording;

import java.time.Instant;
import java.util.UUID;

public record Recording(UUID id, UUID userId, RecordingStatus status, Instant startedAt,
                        Instant endedAt, String viewSecret, Instant countdownEndsAt,
                        Instant alertsDispatchedAt, boolean viewRevoked) {

    /**
     * A recording is "live" if it has not been explicitly ended by the device.
     * INTERRUPTED is live: the phone disconnected but the emergency is still ongoing —
     * alerts still fire and the recording can resume.
     */
    public boolean live() {
        return status == RecordingStatus.PENDING
                || status == RecordingStatus.RECORDING
                || status == RecordingStatus.INTERRUPTED;
    }

    /**
     * True if the recording can be resumed by a publisher reconnect.
     * PENDING and INTERRUPTED are resumable; RECORDING means already active.
     * ENDED and FAILED are terminal — a reconnect must not revive them.
     */
    public boolean resumable() {
        return status == RecordingStatus.PENDING || status == RecordingStatus.INTERRUPTED;
    }

    public Recording streaming() {
        return new Recording(id, userId, RecordingStatus.RECORDING, startedAt, endedAt,
                viewSecret, countdownEndsAt, alertsDispatchedAt, viewRevoked);
    }

    /** Connection lost — not ended, just disconnected. Does NOT set ended_at. */
    public Recording interrupted() {
        return new Recording(id, userId, RecordingStatus.INTERRUPTED, startedAt, null,
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
