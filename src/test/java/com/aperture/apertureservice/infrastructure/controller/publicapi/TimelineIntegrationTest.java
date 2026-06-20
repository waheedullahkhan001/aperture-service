package com.aperture.apertureservice.infrastructure.controller.publicapi;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.PlaybackSource;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class TimelineIntegrationTest {

    // Stub out the real MediaMTX HTTP call — no network needed in tests
    @MockitoBean
    PlaybackSource playbackSource;

    @Autowired MockMvc mvc;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired Recordings recordings;
    @Autowired PasswordHasher passwordHasher;

    // Real MP4 bytes are irrelevant for these tests; use a recognisable payload
    static final byte[] FAKE_MP4 = "FAKE-MP4-CONTENT-FOR-TIMELINE-TEST".getBytes();
    static final String START = "2026-06-19T10:00:00Z";
    static final double DURATION = 65.0;

    UUID userId;

    @BeforeEach
    void seed() throws Exception {
        String email = "timeline-" + UUID.randomUUID() + "@example.com";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Timeline Tester",
                new HashedPassword(passwordHasher.hash("Passw0rd!")), true, Instant.now());
        users.save(u);
        userId = u.id();
        MintedDevice m = connectDevice.connect(userId, "TestPhone");

        // Default stub behaviour: list returns one span, fetch returns FAKE_MP4
        Mockito.when(playbackSource.list(anyString(), anyString()))
                .thenReturn(List.of(new PlaybackSource.Span(START, DURATION)));
        Mockito.when(playbackSource.fetch(anyString(), anyString(), anyDouble(), anyString()))
                .thenReturn(new ByteArrayInputStream(FAKE_MP4));
    }

    private Recording seedRecording() {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        Recording r = new Recording(recId, userId, RecordingStatus.ENDED,
                Instant.parse(START), Instant.now(), "apv_" + UUID.randomUUID(), null, null, false);
        recordings.insertIfAbsent(r);
        return r;
    }

    // ── valid token → 200 with video/mp4 body ────────────────────────────────

    @Test
    void timelineReturns200WithVideoMp4ForValidToken() throws Exception {
        Recording r = seedRecording();

        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", r.viewSecret()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(FAKE_MP4));
    }

    // ── Range request → 206 ──────────────────────────────────────────────────

    @Test
    void timelineRangeRequestReturns206() throws Exception {
        // Each test seeds its own recording to ensure cache isolation.
        Recording r = seedRecording();

        // First: populate the cache with a full 200 fetch
        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", r.viewSecret()))
                .andExpect(status().isOk());

        // Second: Range request on the now-cached file → 206
        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", r.viewSecret())
                        .header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())           // 206
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes("FAKE".getBytes()));   // bytes 0-3 of FAKE_MP4
    }

    // ── wrong token → 403 ────────────────────────────────────────────────────

    @Test
    void timelineWrongTokenReturns403() throws Exception {
        Recording r = seedRecording();

        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", "apv_wrong_token"))
                .andExpect(status().isForbidden());
    }

    // ── revoked token → 403 ──────────────────────────────────────────────────

    @Test
    void timelineRevokedTokenReturns403() throws Exception {
        Recording r = seedRecording();
        // Revoke the watch link
        recordings.save(r.revokedView());

        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", r.viewSecret()))
                .andExpect(status().isForbidden());
    }

    // ── unknown recording → 404 ───────────────────────────────────────────────

    @Test
    void timelineUnknownRecordingReturns404() throws Exception {
        mvc.perform(get("/api/public/watch/{id}/timeline", UUID.randomUUID())
                        .param("t", "apv_anything"))
                .andExpect(status().isNotFound());
    }

    // ── clip params (start + duration) passed through ─────────────────────────

    @Test
    void timelineWithClipParamsPassesThemToPlaybackSource() throws Exception {
        Recording r = seedRecording();
        String clipStart = "2026-06-19T10:05:00Z";
        double clipDuration = 30.0;

        mvc.perform(get("/api/public/watch/{id}/timeline", r.id())
                        .param("t", r.viewSecret())
                        .param("start", clipStart)
                        .param("duration", String.valueOf(clipDuration)))
                .andExpect(status().isOk());

        // Verify the stub was called with the clip params (not the full-recording span)
        Mockito.verify(playbackSource, Mockito.never()).list(anyString(), anyString());
        Mockito.verify(playbackSource).fetch(
                org.mockito.ArgumentMatchers.eq("aperture/" + r.id()),
                org.mockito.ArgumentMatchers.eq(clipStart),
                org.mockito.ArgumentMatchers.eq(clipDuration),
                org.mockito.ArgumentMatchers.eq(r.viewSecret()));
    }
}
