package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.api.AppendMetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.stubs.FixedAlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryMetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordings;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemorySegmentFileStore;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final InMemoryRecordingSegments segments = new InMemoryRecordingSegments();
    private final InMemoryMetadataSamples samples = new InMemoryMetadataSamples();
    private final InMemorySegmentFileStore files = new InMemorySegmentFileStore();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final TelemetryService service = new TelemetryService(recordings, segments, samples, files, clock);

    private final UUID recId = UuidCreator.getTimeOrderedEpoch();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void seed() {
        new RecordingService(recordings, new FixedAlertPolicy(null), new FixedRandomTokens(),
                clock).ensure(recId, userId, null, null);
    }

    @Test
    void appendStoresSamplesWithServerTimeAndChecksOwnership() {
        int n = service.append(recId, userId, List.of(
                new AppendMetadataSamples.NewSample(new BigDecimal("1.5"), new BigDecimal("2.5"),
                        T0.minusSeconds(30), "Pixel", null, null, null, null, null),
                new AppendMetadataSamples.NewSample(null, null, T0, null, null, null, null, null, null)));
        assertThat(n).isEqualTo(2);
        MetadataSample latest = samples.latest(recId).orElseThrow();
        assertThat(latest.serverReceivedAt()).isEqualTo(T0);
        assertThat(latest.clientTimestamp()).isEqualTo(T0);

        assertThatThrownBy(() -> service.append(recId, UUID.randomUUID(), List.of()))
                .isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> service.append(UUID.randomUUID(), userId, List.of()))
                .isInstanceOf(NotFound.class);
    }

    @Test
    void appendCapsBatchSize() {
        List<AppendMetadataSamples.NewSample> tooMany = IntStream.range(0, 501)
                .mapToObj(i -> new AppendMetadataSamples.NewSample(null, null, T0, null, null, null, null, null, null)).toList();
        assertThatThrownBy(() -> service.append(recId, userId, tooMany))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "BATCH_TOO_LARGE");
    }

    @Test
    void segmentCompletedNumbersSequentiallyAndReadsSize() {
        files.put("/data/recordings/aperture/" + recId + "/a.mp4", new byte[]{1, 2, 3});
        service.segmentCompleted(recId, "/data/recordings/aperture/" + recId + "/a.mp4", 30.0);
        service.segmentCompleted(recId, "/data/recordings/aperture/" + recId + "/b.mp4", 12.5);

        List<RecordingSegment> stored = segments.byRecording(recId);
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).segmentNumber()).isEqualTo(1);
        assertThat(stored.get(0).sizeBytes()).isEqualTo(3);
        assertThat(stored.get(0).startTime()).isEqualTo(T0.minusSeconds(30));
        assertThat(stored.get(0).endTime()).isEqualTo(T0);
        assertThat(stored.get(1).segmentNumber()).isEqualTo(2);
        assertThat(stored.get(1).uploaded()).isTrue();
    }

    @Test
    void segmentCompletedIsIdempotentPerPathAndIgnoresUnknownRecording() {
        String path = "/data/recordings/aperture/" + recId + "/a.mp4";
        service.segmentCompleted(recId, path, 30.0);
        service.segmentCompleted(recId, path, 30.0);   // duplicate hook delivery
        assertThat(segments.byRecording(recId)).hasSize(1);

        service.segmentCompleted(UUID.randomUUID(), "/x.mp4", 30.0); // unknown: silently ignored
    }
}
