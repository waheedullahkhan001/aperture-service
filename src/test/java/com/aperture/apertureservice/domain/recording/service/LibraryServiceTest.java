package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.RecordingDetail;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.spi.stubs.FixedAlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryMetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordings;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemorySegmentFileStore;
import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final InMemoryRecordingSegments segments = new InMemoryRecordingSegments();
    private final InMemoryMetadataSamples samples = new InMemoryMetadataSamples();
    private final InMemorySegmentFileStore files = new InMemorySegmentFileStore();
    private final LibraryService service = new LibraryService(recordings, segments, samples, files);
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private final UUID userId = UUID.randomUUID();

    private UUID seedRecording() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        new RecordingService(recordings, new FixedAlertPolicy(null), new FixedRandomTokens(), clock)
                .ensure(id, userId, null);
        return id;
    }

    @Test
    void listPagesNewestFirstAndFiltersByStatus() {
        seedRecording();
        seedRecording();
        PageOf<Recording> all = service.list(userId, Optional.empty(), 0, 10);
        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(service.list(userId, Optional.of(RecordingStatus.ENDED), 0, 10).totalElements()).isZero();
    }

    @Test
    void detailIncludesSegmentsAndSamplesAndChecksOwnership() {
        UUID id = seedRecording();
        segments.save(new RecordingSegment(null, id, 1, "/p/a.mp4", T0, T0.plusSeconds(30), 3, true, SegmentSource.STREAMED, null, null));
        samples.saveAll(List.of(new MetadataSample(null, id, null, null, T0, T0, null, null, null, null, null, null)));

        RecordingDetail detail = service.get(userId, id);
        assertThat(detail.segments()).hasSize(1);
        assertThat(detail.recentSamples()).hasSize(1);
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), id)).isInstanceOf(Forbidden.class);
    }

    @Test
    void downloadOpensOwnedSegmentFile() throws Exception {
        UUID id = seedRecording();
        files.put("/p/a.mp4", new byte[]{9, 9});
        segments.save(new RecordingSegment(null, id, 1, "/p/a.mp4", T0, T0, 2, true, SegmentSource.STREAMED, null, null));

        SegmentDownload dl = service.download(userId, id, 1);
        assertThat(dl.stream().readAllBytes()).containsExactly(9, 9);
        assertThat(dl.filename()).isEqualTo(id + "-1.mp4");
        assertThatThrownBy(() -> service.download(userId, id, 2)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> service.download(UUID.randomUUID(), id, 1)).isInstanceOf(Forbidden.class);
    }

    @Test
    void deleteRemovesFilesAndRows() {
        UUID id = seedRecording();
        files.put("/p/a.mp4", new byte[]{1});
        segments.save(new RecordingSegment(null, id, 1, "/p/a.mp4", T0, T0, 1, true, SegmentSource.STREAMED, null, null));
        samples.saveAll(List.of(new MetadataSample(null, id, null, null, T0, T0, null, null, null, null, null, null)));

        service.delete(userId, id);
        assertThat(files.exists("/p/a.mp4")).isFalse();
        assertThat(segments.byRecording(id)).isEmpty();
        assertThat(samples.latest(id)).isEmpty();
        assertThat(recordings.byId(id)).isEmpty();
    }
}
