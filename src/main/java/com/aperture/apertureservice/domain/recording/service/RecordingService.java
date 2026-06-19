package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.domain.recording.CancelResult;
import com.aperture.apertureservice.domain.recording.EnsureResult;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.api.CancelAlerts;
import com.aperture.apertureservice.domain.recording.api.EndRecording;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.domain.recording.api.MarkStalledRecordings;
import com.aperture.apertureservice.domain.recording.api.MarkStreaming;
import com.aperture.apertureservice.domain.recording.spi.AlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@DomainService
public class RecordingService implements EnsureRecording, MarkStreaming, EndRecording, MarkStalledRecordings, CancelAlerts {

    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final Recordings recordings;
    private final AlertPolicy alertPolicy;
    private final RandomTokens tokens;
    private final Clock clock;

    public RecordingService(Recordings recordings, AlertPolicy alertPolicy,
                            RandomTokens tokens, Clock clock) {
        this.recordings = recordings;
        this.alertPolicy = alertPolicy;
        this.tokens = tokens;
        this.clock = clock;
    }

    // NOTE: deliberately NOT @Transactional — insertIfAbsent commits in its own short
    // transaction inside the JPA adapter (REQUIRES_NEW) so a duplicate-key conflict cannot
    // poison an outer transaction. See the recording persistence adapter.
    @Override
    public EnsureResult ensure(UUID recordingId, UUID userId, Instant clientStartedAt) {
        Instant now = clock.instant();
        Recording candidate = new Recording(recordingId, userId, RecordingStatus.PENDING,
                clientStartedAt != null ? clientStartedAt : now, null,
                tokens.token("apv_"),
                alertPolicy.activeCountdownFor(userId).map(now::plus).orElse(null),
                null, false);
        if (recordings.insertIfAbsent(candidate)) {
            return new EnsureResult(candidate, true);
        }
        Recording existing = recordings.byId(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!existing.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        return new EnsureResult(existing, false);
    }

    @Override
    @Transactional
    public void markStreaming(UUID recordingId, UUID userId) {
        Recording r = owned(recordingId, userId);
        // Known benign race: a concurrent end could commit between this read and the save,
        // leaving a zombie RECORDING row. MediaMTX fires publish-start before publish-end per
        // session, and the stale sweeper FAILs any zombie within minutes — accepted for MVP.
        if (r.status() == RecordingStatus.PENDING) {
            recordings.save(r.streaming());
        }
    }

    @Override
    @Transactional
    public void end(UUID recordingId, UUID userId) {
        endInternal(owned(recordingId, userId));
    }

    @Override
    @Transactional
    public void endAsSystem(UUID recordingId) {
        recordings.byId(recordingId).ifPresent(this::endInternal);
    }

    private void endInternal(Recording r) {
        if (r.live()) {
            recordings.save(r.ended(clock.instant()));
        }
    }

    @Override
    @Transactional
    public int sweep() {
        Instant now = clock.instant();
        Instant before = now.minus(STALE_AFTER);
        int count = 0;
        for (Recording r : recordings.stalePending(before)) {
            recordings.save(r.failed(now));
            count++;
        }
        for (Recording r : recordings.staleStreaming(before)) {
            recordings.save(r.failed(now));
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public CancelResult cancelAlerts(UUID recordingId, UUID userId) {
        // row lock serializes against the dispatcher: after we commit a disarm, its
        // under-lock guard (countdownEndsAt == null) makes a stale due-list entry a no-op
        Recording r = recordings.byIdForUpdate(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!r.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        if (r.alertsDispatchedAt() != null) {
            return new CancelResult(false, true);   // UC-08 1b: already sent, cannot be retracted
        }
        if (r.countdownEndsAt() != null) {
            recordings.save(r.disarmed());
        }
        return new CancelResult(true, false);
    }

    private Recording owned(UUID recordingId, UUID userId) {
        Recording r = recordings.byId(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!r.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        return r;
    }
}
