package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryDevices;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.spi.stubs.FixedAlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryMetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordings;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemorySegmentFileStore;
import com.aperture.apertureservice.domain.account.service.DeviceService;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamAuthServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryDevices devices = new InMemoryDevices();
    private final InMemoryMetadataSamples samples = new InMemoryMetadataSamples();
    private final InMemoryRecordingSegments segments = new InMemoryRecordingSegments();
    private final InMemorySegmentFileStore files = new InMemorySegmentFileStore();
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final DeviceService deviceService = new DeviceService(users, devices, tokens, clock);
    private final RecordingService recordingService =
            new RecordingService(recordings, new FixedAlertPolicy(null), tokens, clock);
    private final StreamAuthService service = new StreamAuthService(deviceService, recordings, users, samples,
            segments, files, "http://localhost:8888", "http://localhost:8889");

    private UUID userId;
    private String deviceToken;
    private final UUID recId = UuidCreator.getTimeOrderedEpoch();

    @BeforeEach
    void seed() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "Owner",
                new HashedPassword("h"), true, T0);
        users.save(u);
        userId = u.id();
        deviceToken = deviceService.connect(userId, "Pixel").token();
    }

    @Test
    void publishAllowedForOwnTokenOnNewOrOwnRecording() {
        service.authorizePublish(deviceToken, recId);            // new id: allowed
        recordingService.ensure(recId, userId, null);
        service.authorizePublish(deviceToken, recId);            // own existing id: allowed
    }

    @Test
    void publishDeniedForForeignRecordingOrBadToken() {
        recordingService.ensure(recId, UUID.randomUUID(), null); // someone else's recording
        assertThatThrownBy(() -> service.authorizePublish(deviceToken, recId))
                .isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> service.authorizePublish("apd_bad", UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(Unauthorized.class);
    }

    @Test
    void viewRequiresExactSecret() {
        Recording r = recordingService.ensure(recId, userId, null).recording();
        service.authorizeView(recId, r.viewSecret());
        assertThatThrownBy(() -> service.authorizeView(recId, "apv_wrong"))
                .isInstanceOf(Forbidden.class).hasFieldOrPropertyWithValue("code", "INVALID_VIEW_TOKEN");
        assertThatThrownBy(() -> service.authorizeView(UUID.randomUUID(), "apv_x"))
                .isInstanceOf(NotFound.class);
    }

    @Test
    void watchViewAssemblesOwnerUrlsAndLatestSample() {
        Recording r = recordingService.ensure(recId, userId, null).recording();
        samples.saveAll(List.of(new MetadataSample(null, recId, new BigDecimal("33.684400"),
                new BigDecimal("73.047900"), T0, T0, "Pixel", null, null, null, null, null)));

        WatchView view = service.watch(recId, r.viewSecret());
        assertThat(view.ownerName()).isEqualTo("Owner");
        assertThat(view.status()).isEqualTo(RecordingStatus.PENDING);
        assertThat(view.latestSample()).isPresent();
        assertThat(view.hlsUrl())
                .isEqualTo("http://localhost:8888/aperture/" + recId + "/index.m3u8?t=" + r.viewSecret());
        assertThat(view.webrtcUrl())
                .isEqualTo("http://localhost:8889/aperture/" + recId + "/whep?t=" + r.viewSecret());
    }

    @Test
    void watchViewSamplesAreChronologicalAndLatestSampleStillPresent() {
        Recording r = recordingService.ensure(recId, userId, null).recording();
        Instant t1 = T0.minusSeconds(60);
        Instant t2 = T0;
        Instant t3 = T0.plusSeconds(30);
        samples.saveAll(List.of(
                new MetadataSample(null, recId, new BigDecimal("33.1"), new BigDecimal("73.1"),
                        t2, t2, "Pixel", null, 1.5, 90.0, null, 80),
                new MetadataSample(null, recId, new BigDecimal("33.0"), new BigDecimal("73.0"),
                        t1, t1, "Pixel", 5.0, null, null, 500.0, 85),
                new MetadataSample(null, recId, new BigDecimal("33.2"), new BigDecimal("73.2"),
                        t3, t3, "Pixel", null, 2.0, 180.0, null, 75)
        ));

        WatchView view = service.watch(recId, r.viewSecret());

        // latestSample still present (the most recent by clientTimestamp)
        assertThat(view.latestSample()).isPresent();
        assertThat(view.latestSample().get().clientTimestamp()).isEqualTo(t3);

        // samples list is chronological (ASC by clientTimestamp)
        assertThat(view.samples()).hasSize(3);
        assertThat(view.samples().get(0).clientTimestamp()).isEqualTo(t1);
        assertThat(view.samples().get(1).clientTimestamp()).isEqualTo(t2);
        assertThat(view.samples().get(2).clientTimestamp()).isEqualTo(t3);
    }

    @Test
    void watchViewSamplesEmptyWhenNoSamplesExist() {
        Recording r = recordingService.ensure(recId, userId, null).recording();

        WatchView view = service.watch(recId, r.viewSecret());

        assertThat(view.samples()).isEmpty();
        assertThat(view.latestSample()).isEmpty();
    }
}
