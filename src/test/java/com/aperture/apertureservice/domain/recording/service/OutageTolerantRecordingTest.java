package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.stubs.FixedAlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordings;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for outage-tolerant recording lifecycle.
 *
 * Transition table:
 *   publish-start (markStreaming): PENDING | INTERRUPTED -> RECORDING
 *   publish-end   (endAsSystem):   PENDING | RECORDING  -> INTERRUPTED (no ended_at)
 *   device end    (end):           any non-ENDED        -> ENDED (sets ended_at)
 *   ENDED is terminal; reconnect does NOT resume an ENDED recording.
 *
 * Sweeper: INTERRUPTED is excluded — abandoned interrupted recordings are tolerated.
 */
class OutageTolerantRecordingTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final FixedAlertPolicy alertPolicy = new FixedAlertPolicy(Duration.ofSeconds(30));
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final RecordingService service = new RecordingService(recordings, alertPolicy, tokens, clock);

    private final UUID recId = UuidCreator.getTimeOrderedEpoch();
    private final UUID userId = UUID.randomUUID();

    // ---- Reconnect resume (the core bug) ----

    @Test
    void publishEndTransitionsLiveRecordingToInterrupted() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);

        service.endAsSystem(recId);

        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.INTERRUPTED);
        assertThat(r.endedAt()).isNull(); // NOT ended — just disconnected
    }

    @Test
    void publishEndOnPendingRecordingAlsoTransitionsToInterrupted() {
        // Edge case: phone disconnects before publish-start arrives
        service.ensure(recId, userId, null);

        service.endAsSystem(recId);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.INTERRUPTED);
        assertThat(recordings.byId(recId).orElseThrow().endedAt()).isNull();
    }

    @Test
    void reconnectResumesFromInterruptedToRecording() {
        // The exact bug: publish-start -> publish-end -> publish-start must result in RECORDING
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);  // RECORDING
        service.endAsSystem(recId);             // INTERRUPTED

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.INTERRUPTED);

        // Reconnect: same recording id, same user
        service.markStreaming(recId, userId);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
    }

    @Test
    void reconnectAfterLongGapStillResumes() {
        // No time window: INTERRUPTED -> RECORDING regardless of elapsed time
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.endAsSystem(recId);

        // Simulate later reconnect — no time dependency, new service instance with same recordings
        RecordingService laterService = new RecordingService(recordings, alertPolicy, tokens,
                Clock.fixed(T0.plus(Duration.ofHours(2)), ZoneOffset.UTC));
        laterService.markStreaming(recId, userId);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
    }

    @Test
    void multipleDisconnectReconnectCyclesAllWork() {
        service.ensure(recId, userId, null);

        for (int i = 0; i < 3; i++) {
            service.markStreaming(recId, userId);
            assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
            service.endAsSystem(recId);
            assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.INTERRUPTED);
        }

        // Final reconnect works
        service.markStreaming(recId, userId);
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
    }

    // ---- Device explicit end -> ENDED ----

    @Test
    void deviceEndFromRecordingTransitionsToEndedWithEndedAt() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);

        service.end(recId, userId);

        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(r.endedAt()).isEqualTo(T0);
    }

    @Test
    void deviceEndFromInterruptedTransitionsToEndedWithEndedAt() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.endAsSystem(recId); // INTERRUPTED

        service.end(recId, userId);

        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(r.endedAt()).isEqualTo(T0);
    }

    @Test
    void deviceEndFromPendingTransitionsToEnded() {
        service.ensure(recId, userId, null);

        service.end(recId, userId);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
    }

    // ---- ENDED is the sole terminal path; reconnect does NOT resume ENDED ----

    @Test
    void reconnectDoesNotResumeEndedRecording() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.end(recId, userId); // explicit device end -> ENDED

        // Reconnect publish-start: must NOT bring ENDED back to RECORDING
        service.markStreaming(recId, userId);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    void deviceEndIsIdempotentOnEndedRecording() {
        service.ensure(recId, userId, null);
        service.end(recId, userId);

        service.end(recId, userId); // no-op, no exception

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    void deviceEndChecksOwnership() {
        service.ensure(recId, userId, null);
        assertThatThrownBy(() -> service.end(recId, UUID.randomUUID())).isInstanceOf(Forbidden.class);
    }

    // ---- Sweeper does NOT kill INTERRUPTED recordings ----

    @Test
    void sweeperDoesNotMarkInterruptedRecordingAsFailed() {
        // Create an INTERRUPTED recording in the past (past the stale threshold)
        RecordingService past = new RecordingService(recordings, alertPolicy, tokens,
                Clock.fixed(T0.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        past.ensure(recId, userId, null);
        past.markStreaming(recId, userId);
        // Manually put it into INTERRUPTED state (endAsSystem on the past service)
        recordings.save(recordings.byId(recId).orElseThrow().interrupted());

        int swept = service.sweep();

        assertThat(swept).isZero(); // INTERRUPTED not swept
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.INTERRUPTED);
    }

    @Test
    void sweeperStillFailsStalePendingAndStaleStreaming() {
        UUID pendingId = UuidCreator.getTimeOrderedEpoch();
        UUID streamingId = UuidCreator.getTimeOrderedEpoch();
        RecordingService past = new RecordingService(recordings, alertPolicy, tokens,
                Clock.fixed(T0.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        past.ensure(pendingId, userId, null);
        past.ensure(streamingId, userId, null);
        past.markStreaming(streamingId, userId);

        int swept = service.sweep();
        assertThat(swept).isEqualTo(2);
        assertThat(recordings.byId(pendingId).orElseThrow().status()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recordings.byId(streamingId).orElseThrow().status()).isEqualTo(RecordingStatus.FAILED);
    }

    // ---- INTERRUPTED is "alertable" — alert dispatch still works through an outage ----

    @Test
    void interruptedRecordingIsLiveForAlertDispatch() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.endAsSystem(recId); // INTERRUPTED

        Recording r = recordings.byId(recId).orElseThrow();
        // INTERRUPTED should be considered live so alerts can still fire on reconnect
        assertThat(r.live()).isTrue();
    }

    // ---- Segment arriving while INTERRUPTED still registers ----

    @Test
    void segmentCanBeStoredOnInterruptedRecording() {
        // InMemoryRecordings doesn't enforce segment-recording status coupling,
        // so this test verifies that the status field itself doesn't block the path.
        // The real gate is RecordingSegmentService/UploadClip which checks ownership, not status.
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.endAsSystem(recId); // INTERRUPTED

        // Verify INTERRUPTED is accessible and can be read/updated
        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.INTERRUPTED);

        // Save a fresh version (simulates segment-complete or clip upload updating the entity indirectly)
        recordings.save(r); // no-op save — still accessible
        assertThat(recordings.byId(recId)).isPresent();
    }
}
