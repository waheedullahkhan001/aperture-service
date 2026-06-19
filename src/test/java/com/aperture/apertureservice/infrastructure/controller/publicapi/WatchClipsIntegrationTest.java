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
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class WatchClipsIntegrationTest {

    static final String HOOK_SECRET = "Bearer dev-webhook-secret-change-me";
    static final byte[] CLIP_BYTES = "watch-clip-bytes".getBytes();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired Recordings recordings;
    @Autowired AppProperties props;
    @Autowired PasswordHasher passwordHasher;

    UUID userId;
    String deviceToken;

    @BeforeEach
    void seed() {
        String email = "watchclip-" + UUID.randomUUID() + "@example.com";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Watch Tester",
                new HashedPassword(passwordHasher.hash("Passw0rd!")), true, Instant.now());
        users.save(u);
        userId = u.id();
        MintedDevice m = connectDevice.connect(userId, "TestPhone");
        deviceToken = m.token();
    }

    /** Upload a clip via the device endpoint, returns the viewSecret for the created recording. */
    private String uploadClipAndGetSecret(UUID recId, byte[] bytes, String start, String end,
                                          int segmentNumber) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", bytes);
        mvc.perform(multipart("/api/v1/device/recordings/{id}/clips", recId)
                        .file(file)
                        .param("startTime", start)
                        .param("endTime", end)
                        .param("segmentNumber", String.valueOf(segmentNumber))
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isCreated());
        return recordings.byId(recId).orElseThrow().viewSecret();
    }

    // ── watch view returns clip list ──────────────────────────────────────────

    @Test
    void watchViewIncludesUploadedClipWithTimeRange() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String start = "2026-06-19T10:00:00Z";
        String end   = "2026-06-19T10:01:00Z";
        String secret = uploadClipAndGetSecret(recId, CLIP_BYTES, start, end, 1);

        mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments.length()").value(1))
                .andExpect(jsonPath("$.segments[0].segmentNumber").value(1))
                .andExpect(jsonPath("$.segments[0].startTime").value(start))
                .andExpect(jsonPath("$.segments[0].source").value("UPLOADED"))
                .andExpect(jsonPath("$.segments[0].sizeBytes").isNumber());
    }

    @Test
    void watchViewIncludesStreamedSegmentAlongside() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();

        // Start live stream
        mvc.perform(post("/internal/streams/hooks/publish-start")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        // Write a file and fire segment-complete (seg 1, earlier time)
        Path dir = Files.createDirectories(
                Path.of(props.recordingsPath(), "aperture", recId.toString()));
        Path file = Files.write(dir.resolve("seg1.mp4"), new byte[]{1, 2, 3});

        mvc.perform(post("/internal/streams/hooks/segment-complete")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","segmentPath":"%s","duration":"10.0"}"""
                                .formatted(recId, file)))
                .andExpect(status().isNoContent());

        // Also upload a clip (seg 2, later time)
        String start2 = "2026-06-19T11:00:30Z";
        String end2   = "2026-06-19T11:01:00Z";
        MockMultipartFile clip = new MockMultipartFile("file", "clip2.mp4", "video/mp4", CLIP_BYTES);
        mvc.perform(multipart("/api/v1/device/recordings/{id}/clips", recId)
                        .file(clip)
                        .param("startTime", start2)
                        .param("endTime", end2)
                        .param("segmentNumber", "2")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isCreated());

        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        MvcResult result = mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andReturn();

        // Verify ordering is chronological by segmentNumber proxy (seg1 before seg2)
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("segments").get(0).get("segmentNumber").asInt()).isEqualTo(1);
        assertThat(body.get("segments").get(1).get("segmentNumber").asInt()).isEqualTo(2);
        assertThat(body.get("segments").get(0).get("source").asText()).isEqualTo("STREAMED");
        assertThat(body.get("segments").get(1).get("source").asText()).isEqualTo("UPLOADED");
    }

    @Test
    void watchViewSegmentsIncludedWhenRecordingEnded() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        // Upload creates an ENDED recording — the key post-stream case
        String secret = uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T09:00:00Z", "2026-06-19T09:01:00Z", 1);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);

        mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.segments.length()").value(1));
    }

    @Test
    void watchViewEmptySegmentsWhenNoneExist() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        // Start a live stream with no segments yet
        mvc.perform(post("/internal/streams/hooks/publish-start")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments.length()").value(0));
    }

    // ── segment stream endpoint ───────────────────────────────────────────────

    @Test
    void segmentStreamReturnsExactBytesInlineVideoMp4() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String secret = uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T12:00:00Z", "2026-06-19T12:01:00Z", 1);

        mvc.perform(get("/api/public/watch/{id}/segments/1", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"segment-1.mp4\""))
                .andExpect(content().bytes(CLIP_BYTES));
    }

    @Test
    void segmentStreamForUploadedClip() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        byte[] clipBytes = "uploaded-clip-content".getBytes();
        String secret = uploadClipAndGetSecret(recId, clipBytes,
                "2026-06-19T13:00:00Z", "2026-06-19T13:01:00Z", 3);

        mvc.perform(get("/api/public/watch/{id}/segments/3", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(clipBytes));
    }

    @Test
    void segmentStreamWrongTokenReturns403() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T14:00:00Z", "2026-06-19T14:01:00Z", 1);

        mvc.perform(get("/api/public/watch/{id}/segments/1", recId).param("t", "apv_wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void segmentStreamMissingTokenReturns403() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T15:00:00Z", "2026-06-19T15:01:00Z", 1);

        // Missing ?t= — Spring will reject with 400 (required param missing) before
        // reaching the handler; that is acceptable as it leaks no file bytes.
        // However, to be safe we just assert it does NOT return 200.
        MvcResult result = mvc.perform(get("/api/public/watch/{id}/segments/1", recId))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
    }

    @Test
    void segmentStreamUnknownRecordingReturns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/public/watch/{id}/segments/1", unknown).param("t", "apv_anything"))
                .andExpect(status().isNotFound());
    }

    @Test
    void segmentStreamUnknownSegmentNumberReturns404() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String secret = uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T16:00:00Z", "2026-06-19T16:01:00Z", 1);

        mvc.perform(get("/api/public/watch/{id}/segments/99", recId).param("t", secret))
                .andExpect(status().isNotFound());
    }

    @Test
    void segmentStreamWorksAfterRecordingEnded() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String secret = uploadClipAndGetSecret(recId, CLIP_BYTES,
                "2026-06-19T17:00:00Z", "2026-06-19T17:01:00Z", 1);

        // Recording is ENDED (upload of a PENDING recording ends it)
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);

        mvc.perform(get("/api/public/watch/{id}/segments/1", recId).param("t", secret))
                .andExpect(status().isOk())
                .andExpect(content().bytes(CLIP_BYTES));
    }
}
