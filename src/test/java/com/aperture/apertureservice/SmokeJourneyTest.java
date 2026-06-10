package com.aperture.apertureservice;

import com.aperture.apertureservice.domain.account.spi.stubs.CapturingEmailSender;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.support.CapturingEmailConfiguration;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfiguration.class)
class SmokeJourneyTest {

    static final String SECRET = "Bearer dev-webhook-secret-change-me";
    static final Pattern CODE = Pattern.compile("(\\d{6})");
    static final Pattern WATCH = Pattern.compile("/watch/([0-9a-f-]{36})\\?t=([\\w-]+)");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingEmailSender emails;
    @Autowired AppProperties props;

    final String userEmail = "smoke-" + UUID.randomUUID() + "@example.com";
    final String contactEmail = "contact-" + UUID.randomUUID() + "@example.com";

    private JsonNode postJson(String path, String body, String bearer, int expected) throws Exception {
        var req = post(path).contentType("application/json").content(body);
        if (bearer != null) req = req.header("Authorization", "Bearer " + bearer);
        String response = mvc.perform(req).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response.isBlank() ? "null" : response);
    }

    @Test
    void fullEmergencyJourney() throws Exception {
        // 1. register + verify (code arrives by email)
        postJson("/api/v1/auth/register", """
                {"email":"%s","fullname":"Smoke Tester","password":"abcdef1!"}""".formatted(userEmail),
                null, 202);
        Matcher code = CODE.matcher(emails.to(userEmail).get(0).body());
        assertThat(code.find()).isTrue();
        postJson("/api/v1/auth/verify-email", """
                {"email":"%s","code":"%s"}""".formatted(userEmail, code.group(1)), null, 204);

        // 2. login
        JsonNode tokens = postJson("/api/v1/auth/login", """
                {"email":"%s","password":"abcdef1!"}""".formatted(userEmail), null, 200);
        String jwt = tokens.get("accessToken").asText();

        // 3. contact + zero countdown + device
        postJson("/api/v1/me/contacts", """
                {"name":"Mom","email":"%s"}""".formatted(contactEmail), jwt, 201);
        mvc.perform(put("/api/v1/me/alert-config").header("Authorization", "Bearer " + jwt)
                        .contentType("application/json")
                        .content("""
                                {"countdownDurationSeconds":0,"messageTemplate":"Help me! {{streamUrl}}"}"""))
                .andExpect(status().isOk());
        JsonNode device = postJson("/api/v1/me/devices", """
                {"name":"Pixel 8"}""", jwt, 201);
        String deviceToken = device.get("token").asText();
        String deviceId = device.get("id").asText();

        // 4. device sees itself
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userFullname").value("Smoke Tester"));

        // 5. parallel session start: REST upsert + publish hook
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        mvc.perform(put("/api/v1/device/recordings/" + recId)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countdownEndsAt").isNotEmpty());
        mvc.perform(post("/internal/streams/hooks/publish-start").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","query":"token=%s"}""".formatted(recId, deviceToken)))
                .andExpect(status().isNoContent());

        // 6. metadata samples from the device
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{"latitude":33.6844,"longitude":73.0479,
                                  "clientTimestamp":"2026-06-07T12:00:00Z","deviceInfo":"Pixel 8"}]}"""))
                .andExpect(status().isAccepted());

        // 7. countdown=0 -> the scheduler (1s in test profile) dispatches the alert
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(emails.to(contactEmail)).isNotEmpty());
        String alertBody = emails.to(contactEmail).get(0).body();
        assertThat(alertBody).startsWith("Help me!");
        Matcher watch = WATCH.matcher(alertBody);
        assertThat(watch.find()).isTrue();
        String viewSecret = watch.group(2);

        // 8. emergency contact opens the watch endpoint (no account)
        mvc.perform(get("/api/public/watch/" + recId + "?t=" + viewSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Smoke Tester"))
                .andExpect(jsonPath("$.status").value("RECORDING"))
                .andExpect(jsonPath("$.latestSample.latitude").value(33.6844));

        // 9. MediaMTX finishes a segment (real file on disk)
        Path dir = Files.createDirectories(Path.of(props.recordingsPath(), "aperture", recId.toString()));
        Path segment = Files.write(dir.resolve("seg1.mp4"), new byte[]{5, 4, 3, 2, 1});
        mvc.perform(post("/internal/streams/hooks/segment-complete").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s","segmentPath":"%s","duration":"30.0"}"""
                                .formatted(recId, segment)))
                .andExpect(status().isNoContent());

        // 10. stop: publish-end hook
        mvc.perform(post("/internal/streams/hooks/publish-end").header("Authorization", SECRET)
                        .contentType("application/json")
                        .content("""
                                {"path":"aperture/%s"}""".formatted(recId)))
                .andExpect(status().isNoContent());

        // 11. web: list, detail, download
        mvc.perform(get("/api/v1/recordings").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ENDED"));
        mvc.perform(get("/api/v1/recordings/" + recId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[0].sizeBytes").value(5));
        byte[] downloaded = mvc.perform(get("/api/v1/recordings/" + recId + "/segments/1/download")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).containsExactly(5, 4, 3, 2, 1);

        // 12. delete recording: rows AND file gone
        mvc.perform(delete("/api/v1/recordings/" + recId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/recordings/" + recId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
        assertThat(Files.exists(segment)).isFalse();

        // 13. revoke device -> device surface dies with a distinct code
        mvc.perform(delete("/api/v1/me/devices/" + deviceId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_REVOKED"));

        // 14. delete account -> login impossible
        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/login", """
                {"email":"%s","password":"abcdef1!"}""".formatted(userEmail), null, 401);
    }
}
