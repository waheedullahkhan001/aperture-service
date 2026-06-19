package com.aperture.apertureservice.infrastructure.controller.device;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ClipUploadIntegrationTest {

    static final String HOOK_SECRET = "Bearer dev-webhook-secret-change-me";
    static final String CLIP_CONTENT = "fake-mp4-bytes";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired Recordings recordings;
    @Autowired RecordingSegments segments;
    @Autowired AppProperties props;
    @Autowired PasswordHasher passwordHasher;

    UUID userId;
    String deviceToken;
    String userJwt;

    @BeforeEach
    void seed() throws Exception {
        String email = "cliptest-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Clip Tester",
                new HashedPassword(passwordHasher.hash(password)), true, Instant.now());
        users.save(u);
        userId = u.id();

        MintedDevice m = connectDevice.connect(userId, "TestPhone");
        deviceToken = m.token();

        // Login via HTTP to obtain JWT for the user chain
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = json.readTree(loginResult.getResponse().getContentAsString());
        userJwt = tokens.get("accessToken").asText();
    }

    private MvcResult uploadClip(UUID recId, byte[] bytes, String filename,
                                 String startTime, String endTime,
                                 String clipId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "video/mp4", bytes);
        var request = multipart("/api/v1/device/recordings/{id}/clips", recId)
                .file(file)
                .param("startTime", startTime)
                .param("endTime", endTime)
                .param("clipId", clipId)
                .header("Authorization", "Bearer " + deviceToken);
        return mvc.perform(request).andReturn();
    }

    @Test
    void uploadCreatesRecordingAndSegmentAndFile() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String start = "2026-06-19T10:00:00Z";
        String end   = "2026-06-19T10:01:00Z";

        MvcResult result = uploadClip(recId, CLIP_CONTENT.getBytes(), "clip1.mp4", start, end, "clip-1");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("segmentNumber").asInt()).isEqualTo(1); // server-assigned; first in recording
        assertThat(body.get("source").asText()).isEqualTo("UPLOADED");

        // Recording exists and is ENDED
        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(r.userId()).isEqualTo(userId);

        // Segment is in DB with source=UPLOADED
        List<RecordingSegment> segs = segments.byRecording(recId);
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).source()).isEqualTo(SegmentSource.UPLOADED);
        assertThat(segs.get(0).uploaded()).isTrue();

        // File is on disk
        String filePath = segs.get(0).filePath();
        assertThat(Files.exists(Path.of(filePath))).isTrue();
        assertThat(Files.readAllBytes(Path.of(filePath))).isEqualTo(CLIP_CONTENT.getBytes());
    }

    @Test
    void uploadAcceptsFractionalSecondTimestamps() throws Exception {
        // Android sends Instant.toString(), which carries fractional seconds when present
        // (e.g. ...:00.123Z). Confirm the @RequestParam Instant parser accepts them and
        // round-trips. NOTE: storage is Postgres microsecond precision, so values are kept to
        // 6 fractional digits — fine for Android's millisecond-precision Instant; sub-micro
        // (nanosecond) input would be truncated.
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String start = "2026-06-19T10:00:00.123Z";
        String end   = "2026-06-19T10:00:30.456Z";

        MvcResult result = uploadClip(recId, CLIP_CONTENT.getBytes(), "frac.mp4", start, end, "clip-frac");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        RecordingSegment seg = segments.byRecording(recId).get(0);
        assertThat(seg.startTime()).isEqualTo(Instant.parse(start));
        assertThat(seg.endTime()).isEqualTo(Instant.parse(end));
    }

    @Test
    void idempotentReuploadDoesNotDuplicate() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String start = "2026-06-19T11:00:00Z";
        String end   = "2026-06-19T11:01:00Z";

        // First upload
        MvcResult first = uploadClip(recId, CLIP_CONTENT.getBytes(), "clip1.mp4", start, end, "clip-idem-1");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode firstBody = json.readTree(first.getResponse().getContentAsString());

        // Second upload with same clipId — must not duplicate
        MvcResult second = uploadClip(recId, "different-bytes".getBytes(), "clip1_v2.mp4", start, end, "clip-idem-1");
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        JsonNode secondBody = json.readTree(second.getResponse().getContentAsString());

        assertThat(secondBody.get("segmentId").asLong()).isEqualTo(firstBody.get("segmentId").asLong());
        assertThat(segments.byRecording(recId)).hasSize(1);
    }

    @Test
    void anotherUsersRecordingIdReturns403() throws Exception {
        // Create a recording owned by a different user
        UUID otherUserId = UUID.randomUUID();
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        Recording foreign = new Recording(recId, otherUserId, RecordingStatus.PENDING,
                Instant.now(), null, "apv_foreign_" + UUID.randomUUID(), null, null);
        recordings.insertIfAbsent(foreign);

        MvcResult result = uploadClip(recId, CLIP_CONTENT.getBytes(), "clip.mp4",
                "2026-06-19T12:00:00Z", "2026-06-19T12:01:00Z", "clip-foreign-1");
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void uploadToLiveRecordingDoesNotEndIt() throws Exception {
        // Mid-emergency gap-fill (SRS-036): clips stream in while the recording is still RECORDING.
        // The upload must add the segment WITHOUT ending the live recording — only the streaming
        // lifecycle (publish-end) ends those. (Regression: previously any upload ended a live one.)
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        Recording liveRec = new Recording(recId, userId, RecordingStatus.RECORDING,
                Instant.now(), null, "apv_live_" + UUID.randomUUID(), null, null);
        recordings.insertIfAbsent(liveRec);

        MvcResult result = uploadClip(recId, CLIP_CONTENT.getBytes(), "gap.mp4",
                "2026-06-19T14:00:00Z", "2026-06-19T14:00:30Z", "clip-gap-7");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(segments.byRecording(recId)).anyMatch(s -> s.source() == SegmentSource.UPLOADED);
    }

    @Test
    void downloadUploadedSegmentReturnsBytes() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        uploadClip(recId, CLIP_CONTENT.getBytes(), "clip1.mp4",
                "2026-06-19T13:00:00Z", "2026-06-19T13:01:00Z", "clip-dl-1");

        mvc.perform(get("/api/v1/recordings/{id}/segments/1/download", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isOk())
                .andExpect(content().bytes(CLIP_CONTENT.getBytes()));
    }

    /**
     * Regression: on one recording, a streamed segment (number=1) and an uploaded clip used
     * to share one number-space. The phone supplies number=1 for its first gap clip, which
     * collides with the streamed segment. Before the fix the idempotency check found the
     * streamed segment and silently dropped the upload. Now the server assigns the next
     * available number, so both segments are stored with distinct numbers.
     */
    @Test
    void streamedAndUploadedOnSameRecordingAreBothStored() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();

        // 1. Start a live stream
        mvc.perform(post("/internal/streams/hooks/publish-start")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        // 2. Fire one segment-complete — this creates streamed segment #1
        Path dir = Files.createDirectories(
                Path.of(props.recordingsPath(), "aperture", recId.toString()));
        Path segFile = Files.write(dir.resolve("seg1.mp4"), new byte[]{1, 2, 3});
        mvc.perform(post("/internal/streams/hooks/segment-complete")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","segmentPath":"%s","duration":"5.0"}"""
                                .formatted(recId, segFile)))
                .andExpect(status().isNoContent());

        // 3. Upload a gap clip — the phone supplies clipId="gap-1". Prior to the fix, the server
        //    would have compared number=1 (phone-supplied) against the already-stored streamed
        //    segment #1 and silently returned it, dropping the upload entirely.
        MvcResult upload = uploadClip(recId, CLIP_CONTENT.getBytes(), "gap.mp4",
                "2026-06-19T20:00:00Z", "2026-06-19T20:00:30Z", "gap-1");
        assertThat(upload.getResponse().getStatus()).isEqualTo(201);

        // 4. Both segments must be in the DB with distinct segment numbers
        List<RecordingSegment> stored = segments.byRecording(recId);
        assertThat(stored).hasSize(2);

        List<Integer> numbers = stored.stream().map(RecordingSegment::segmentNumber).sorted().toList();
        assertThat(numbers).containsExactly(1, 2); // distinct numbers

        List<SegmentSource> sources = stored.stream()
                .sorted(java.util.Comparator.comparingInt(RecordingSegment::segmentNumber))
                .map(RecordingSegment::source).toList();
        assertThat(sources).containsExactly(SegmentSource.STREAMED, SegmentSource.UPLOADED);

        // 5. The uploaded clip response must report its server-assigned number (2, not 1)
        JsonNode body = json.readTree(upload.getResponse().getContentAsString());
        assertThat(body.get("segmentNumber").asInt()).isEqualTo(2);
    }

    @Test
    void differentClipIdsCreateDistinctSegments() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String start = "2026-06-19T21:00:00Z";
        String end   = "2026-06-19T21:01:00Z";

        MvcResult first = uploadClip(recId, CLIP_CONTENT.getBytes(), "a.mp4", start, end, "clip-A");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode firstBody = json.readTree(first.getResponse().getContentAsString());

        MvcResult second = uploadClip(recId, "other-bytes".getBytes(), "b.mp4", start, end, "clip-B");
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        JsonNode secondBody = json.readTree(second.getResponse().getContentAsString());

        // Different clipIds → different segments with different server-assigned numbers
        assertThat(secondBody.get("segmentId").asLong()).isNotEqualTo(firstBody.get("segmentId").asLong());
        assertThat(secondBody.get("segmentNumber").asInt()).isNotEqualTo(firstBody.get("segmentNumber").asInt());
        assertThat(segments.byRecording(recId)).hasSize(2);
    }

    @Test
    void streamedSegmentIsTaggedStreamed() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();

        // Start a stream via hook to create the recording
        mvc.perform(post("/internal/streams/hooks/publish-start")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        // Write a file and fire segment-complete
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

        List<RecordingSegment> stored = segments.byRecording(recId);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).source()).isEqualTo(SegmentSource.STREAMED);
    }
}
