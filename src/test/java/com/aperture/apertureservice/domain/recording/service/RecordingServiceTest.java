package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.recording.CancelResult;
import com.aperture.apertureservice.domain.recording.EnsureResult;
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

class RecordingServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final FixedAlertPolicy alertPolicy = new FixedAlertPolicy(Duration.ofSeconds(30));
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final RecordingService service = new RecordingService(recordings, alertPolicy, tokens, clock);

    private final UUID recId = UuidCreator.getTimeOrderedEpoch();
    private final UUID userId = UUID.randomUUID();

    @Test
    void ensureCreatesPendingWithViewSecretAndCountdown() {
        EnsureResult result = service.ensure(recId, userId, T0.minusSeconds(2));

        assertThat(result.created()).isTrue();
        Recording r = result.recording();
        assertThat(r.status()).isEqualTo(RecordingStatus.PENDING);
        assertThat(r.startedAt()).isEqualTo(T0.minusSeconds(2));     // client clock honored
        assertThat(r.viewSecret()).startsWith("apv_");
        assertThat(r.countdownEndsAt()).isEqualTo(T0.plusSeconds(30)); // server clock anchors countdown
        assertThat(r.alertsDispatchedAt()).isNull();
    }

    @Test
    void ensureWithoutContactsHasNoCountdown() {
        alertPolicy.set(null);
        assertThat(service.ensure(recId, userId, null).recording().countdownEndsAt()).isNull();
    }

    @Test
    void ensureZeroCountdownIsImmediatelyDue() {
        alertPolicy.set(Duration.ZERO);
        Recording r = service.ensure(recId, userId, null).recording();
        assertThat(r.countdownEndsAt()).isEqualTo(T0);
        assertThat(recordings.dispatchDue(T0)).containsExactly(r);
    }

    @Test
    void ensureIsIdempotent() {
        Recording first = service.ensure(recId, userId, null).recording();
        EnsureResult second = service.ensure(recId, userId, T0.plusSeconds(5));
        assertThat(second.created()).isFalse();
        assertThat(second.recording()).isEqualTo(first);
    }

    @Test
    void ensureRejectsForeignRecordingId() {
        service.ensure(recId, userId, null);
        assertThatThrownBy(() -> service.ensure(recId, UUID.randomUUID(), null))
                .isInstanceOf(Forbidden.class).hasFieldOrPropertyWithValue("code", "RECORDING_FORBIDDEN");
    }

    @Test
    void markStreamingUpgradesPendingOnlyAndChecksOwner() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);

        service.markStreaming(recId, userId); // idempotent
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);

        assertThatThrownBy(() -> service.markStreaming(recId, UUID.randomUUID()))
                .isInstanceOf(Forbidden.class);
    }

    @Test
    void markStreamingDoesNotResurrectEndedRecording() {
        service.ensure(recId, userId, null);
        service.endAsSystem(recId);
        service.markStreaming(recId, userId);
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    void endTransitionsLiveStatesAndIsIdempotent() {
        service.ensure(recId, userId, null);
        service.markStreaming(recId, userId);
        service.end(recId, userId);
        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(r.endedAt()).isEqualTo(T0);

        service.end(recId, userId); // no-op
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
        assertThatThrownBy(() -> service.end(recId, UUID.randomUUID())).isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> service.end(UUID.randomUUID(), userId)).isInstanceOf(NotFound.class);
    }

    @Test
    void ensureOnTerminalRowReturnsItWithoutResurrection() {
        service.ensure(recId, userId, null);
        service.endAsSystem(recId);
        EnsureResult again = service.ensure(recId, userId, null);
        assertThat(again.created()).isFalse();
        assertThat(again.recording().status()).isEqualTo(RecordingStatus.ENDED); // device sees terminal state
    }

    @Test
    void cancelAlertsDisarmsCountdownAndIsIdempotent() {
        service.ensure(recId, userId, null);                       // FixedAlertPolicy arms 30s countdown
        assertThat(recordings.dispatchDue(T0.plusSeconds(31))).hasSize(1);

        CancelResult first = service.cancelAlerts(recId, userId);
        assertThat(first.cancelled()).isTrue();
        assertThat(first.alertsAlreadyDispatched()).isFalse();
        assertThat(recordings.byId(recId).orElseThrow().countdownEndsAt()).isNull();
        assertThat(recordings.dispatchDue(T0.plusSeconds(31))).isEmpty();

        CancelResult again = service.cancelAlerts(recId, userId);  // idempotent
        assertThat(again.cancelled()).isTrue();
    }

    @Test
    void cancelAlertsAfterDispatchReportsIrreversible() {
        service.ensure(recId, userId, null);
        recordings.save(recordings.byId(recId).orElseThrow().dispatched(T0));
        CancelResult result = service.cancelAlerts(recId, userId);
        assertThat(result.cancelled()).isFalse();
        assertThat(result.alertsAlreadyDispatched()).isTrue();
    }

    @Test
    void cancelAlertsChecksOwnership() {
        service.ensure(recId, userId, null);
        assertThatThrownBy(() -> service.cancelAlerts(recId, UUID.randomUUID())).isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> service.cancelAlerts(UUID.randomUUID(), userId)).isInstanceOf(NotFound.class);
    }

    @Test
    void sweepFailsStalePendingAndStaleStreaming() {
        UUID pendingId = UuidCreator.getTimeOrderedEpoch();
        UUID streamingId = UuidCreator.getTimeOrderedEpoch();
        // ensure with a service whose clock is 10 minutes in the past
        RecordingService past = new RecordingService(recordings, alertPolicy, tokens,
                Clock.fixed(T0.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        past.ensure(pendingId, userId, null);
        past.ensure(streamingId, userId, null);
        past.markStreaming(streamingId, userId);

        int swept = service.sweep();
        assertThat(swept).isEqualTo(2);
        assertThat(recordings.byId(pendingId).orElseThrow().status()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recordings.byId(streamingId).orElseThrow().status()).isEqualTo(RecordingStatus.FAILED);
        assertThat(recordings.byId(streamingId).orElseThrow().endedAt()).isEqualTo(T0);
    }
}
