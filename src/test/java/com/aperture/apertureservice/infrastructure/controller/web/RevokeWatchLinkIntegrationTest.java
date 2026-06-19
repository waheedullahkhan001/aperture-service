package com.aperture.apertureservice.infrastructure.controller.web;

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
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class RevokeWatchLinkIntegrationTest {

    static final String HOOK_SECRET = "Bearer dev-webhook-secret-change-me";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired Recordings recordings;
    @Autowired PasswordHasher passwordHasher;

    UUID userId;
    String deviceToken;
    String userJwt;

    @BeforeEach
    void seed() throws Exception {
        String email = "revoke-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Revoke Tester",
                new HashedPassword(passwordHasher.hash(password)), true, Instant.now());
        users.save(u);
        userId = u.id();

        MintedDevice m = connectDevice.connect(userId, "TestPhone");
        deviceToken = m.token();

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = json.readTree(loginResult.getResponse().getContentAsString());
        userJwt = tokens.get("accessToken").asText();
    }

    private UUID startRecording() throws Exception {
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        mvc.perform(post("/internal/streams/hooks/publish-start")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());
        return recId;
    }

    // ── sanity: watch works BEFORE revoke ──────────────────────────────────────

    @Test
    void watchLinkWorksBeforeRevoke() throws Exception {
        UUID recId = startRecording();
        String secret = recordings.byId(recId).orElseThrow().viewSecret();
        mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isOk());
    }

    // ── revoke endpoint ────────────────────────────────────────────────────────

    @Test
    void revokeReturns204ForOwner() throws Exception {
        UUID recId = startRecording();
        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokeIsIdempotent() throws Exception {
        UUID recId = startRecording();
        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokeUnknownRecordingReturns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", unknown)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeOtherUsersRecordingReturns403() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        Recording foreign = new Recording(recId, otherUserId, RecordingStatus.PENDING,
                Instant.now(), null, "apv_foreign_" + UUID.randomUUID(), null, null, false);
        recordings.insertIfAbsent(foreign);

        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeRequiresAuth() throws Exception {
        UUID recId = startRecording();
        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId))
                .andExpect(status().isUnauthorized());
    }

    // ── enforcement: watch view blocked after revoke ───────────────────────────

    @Test
    void watchViewBlockedAfterRevoke() throws Exception {
        UUID recId = startRecording();
        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/public/watch/{id}", recId).param("t", secret))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIEW_REVOKED"));
    }

    // ── enforcement: public segment stream blocked after revoke ───────────────

    @Test
    void segmentStreamBlockedAfterRevoke() throws Exception {
        UUID recId = startRecording();
        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/public/watch/{id}/segments/1", recId).param("t", secret))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIEW_REVOKED"));
    }

    // ── enforcement: live HLS/WebRTC read auth blocked after revoke ───────────

    @Test
    void liveReadAuthBlockedAfterRevoke() throws Exception {
        UUID recId = startRecording();
        String secret = recordings.byId(recId).orElseThrow().viewSecret();

        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());

        String authBody = """
                {"user":"","password":"","ip":"10.0.0.5","action":"read","path":"aperture/%s","protocol":"rtsp","id":"c1","query":"t=%s"}"""
                .formatted(recId, secret);
        mvc.perform(post("/internal/streams/auth")
                        .header("Authorization", HOOK_SECRET)
                        .contentType("application/json")
                        .content(authBody))
                .andExpect(status().isForbidden());
    }

    // ── viewRevoked field appears in owner detail response ────────────────────

    @Test
    void detailResponseShowsViewRevokedFlag() throws Exception {
        UUID recId = startRecording();

        mvc.perform(get("/api/v1/recordings/{id}", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recording.viewRevoked").value(false));

        mvc.perform(post("/api/v1/recordings/{id}/revoke-link", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/recordings/{id}", recId)
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recording.viewRevoked").value(true));
    }
}
