package com.aperture.apertureservice.infrastructure.controller.internal;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.api.RevokeDevice;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.emergency.api.AddEmergencyContact;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class MediaMtxContractTest {

    static final String SECRET = "Bearer dev-webhook-secret-change-me";

    @Autowired MockMvc mvc;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired RevokeDevice revokeDevice;
    @Autowired AddEmergencyContact addContact;
    @Autowired Recordings recordings;
    @Autowired RecordingSegments segments;
    @Autowired AppProperties props;

    UUID userId;
    UUID deviceId;
    String deviceToken;

    @BeforeEach
    void seed() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email("mtx-" + UUID.randomUUID() + "@example.com"), "Mtx Owner",
                new HashedPassword("h"), true, Instant.now());
        users.save(u);
        userId = u.id();
        MintedDevice m = connectDevice.connect(userId, "Pixel");
        deviceId = m.id();
        deviceToken = m.token();
    }

    private String authBody(String action, String path, String query) {
        return """
                {"user":"","password":"","ip":"10.0.0.5","action":"%s","path":"%s","protocol":"rtsp","id":"c1","query":"%s"}"""
                .formatted(action, path, query);
    }

    @Test
    void authEndpointDoesNotRequireWebhookSecret() throws Exception {
        // /internal/streams/auth is intentionally excluded from WebhookSecretFilter:
        // MediaMTX's auth callback has no way to send a custom header, and the endpoint
        // validates stream tokens directly (so the webhook secret adds no security here).
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        // no Authorization header, but valid token in query -> allowed
        mvc.perform(post("/internal/streams/auth").contentType("application/json")
                        .content(authBody("publish", "aperture/" + recId, "token=" + deviceToken)))
                .andExpect(status().isNoContent());
        // no Authorization header, invalid path -> 403 (controller, not filter)
        mvc.perform(post("/internal/streams/auth").contentType("application/json")
                        .content(authBody("publish", "not-aperture/xyz", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishAuthMatrix() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        // valid token, new id -> allow
        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("publish", "aperture/" + recId, "token=" + deviceToken)))
                .andExpect(status().isNoContent());
        // foreign recording id -> deny 403
        Recording foreign = new Recording(UuidCreator.getTimeOrderedEpoch(), UUID.randomUUID(),
                RecordingStatus.PENDING, Instant.now(), null, "apv_f_" + UUID.randomUUID(), null, null);
        recordings.insertIfAbsent(foreign);
        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("publish", "aperture/" + foreign.id(), "token=" + deviceToken)))
                .andExpect(status().isForbidden());
        // revoked device -> 401
        revokeDevice.revoke(userId, deviceId);
        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("publish", "aperture/" + recId, "token=" + deviceToken)))
                .andExpect(status().isUnauthorized());
        // unknown action -> 403
        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("api", "aperture/" + recId, "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishStartCreatesRecordingWithCountdownAndIsIdempotent() throws Exception {
        addContact.add(userId, "Mom", "mom-" + UUID.randomUUID() + "@example.com", null);
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String body = """
                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken);

        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        Recording r = recordings.byId(recId).orElseThrow();
        assertThat(r.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(r.countdownEndsAt()).isNotNull();   // contacts configured -> countdown armed

        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());     // duplicate hook: no error, single row
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
    }

    @Test
    void readAuthUsesViewSecret() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());
        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("read", "aperture/" + recId, "t=" + secret)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/internal/streams/auth").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content(authBody("read", "aperture/" + recId, "t=apv_wrong")))
                .andExpect(status().isForbidden());
    }

    @Test
    void segmentCompleteAndPublishEndLifecycle() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        Path dir = Files.createDirectories(Path.of(props.recordingsPath(), "aperture", recId.toString()));
        Path file = Files.write(dir.resolve("seg1.mp4"), new byte[]{1, 2, 3, 4});

        mvc.perform(post("/internal/streams/hooks/segment-complete").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","segmentPath":"%s","duration":"30.0"}"""
                                .formatted(recId, file)))
                .andExpect(status().isNoContent());
        List<RecordingSegment> stored = segments.byRecording(recId);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).segmentNumber()).isEqualTo(1);
        assertThat(stored.get(0).sizeBytes()).isEqualTo(4);

        mvc.perform(post("/internal/streams/hooks/publish-end").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s"}""".formatted(recId)))
                .andExpect(status().isNoContent());
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.ENDED);
    }

    @Test
    void publishStartWithoutTokenIsRejected() throws Exception {
        // the most plausible real misconfiguration: MediaMTX hook missing $MTX_QUERY
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":""}""".formatted(UuidCreator.getTimeOrderedEpoch())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"not-aperture/xyz","query":"token=apd_x"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishStartAcceptsUrlEncodedQuery() throws Exception {
        // mediamtx sends $MTX_QUERY url-encoded: "token=apd_..." becomes "token%3Dapd_..."
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        String encodedQuery = "token%3D" + deviceToken;   // %3D = '='
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"%s"}""".formatted(recId, encodedQuery)))
                .andExpect(status().isNoContent());
        assertThat(recordings.byId(recId).orElseThrow().status()).isEqualTo(RecordingStatus.RECORDING);
    }
}
