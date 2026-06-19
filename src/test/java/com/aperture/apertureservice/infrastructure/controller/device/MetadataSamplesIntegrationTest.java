package com.aperture.apertureservice.infrastructure.controller.device;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class MetadataSamplesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired MetadataSamples metadataSamples;
    @Autowired PasswordHasher passwordHasher;

    UUID userId;
    UUID recId;
    String deviceToken;
    String viewSecret;

    @BeforeEach
    void seed() throws Exception {
        String email = "metasamples-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Tester",
                new HashedPassword(passwordHasher.hash(password)), true, Instant.now());
        users.save(u);
        userId = u.id();

        MintedDevice m = connectDevice.connect(userId, "TestPhone");
        deviceToken = m.token();

        recId = UuidCreator.getTimeOrderedEpoch();
        MvcResult ensureResult = mvc.perform(put("/api/v1/device/recordings/" + recId)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode ensureBody = json.readTree(ensureResult.getResponse().getContentAsString());
        // watchUrl is like http://localhost/watch/{id}?t=<secret>
        String watchUrl = ensureBody.get("watchUrl").asText();
        viewSecret = watchUrl.substring(watchUrl.indexOf("?t=") + 3);
    }

    @Test
    void samplesWithResponderFieldsAreStoredAndReturned() throws Exception {
        // POST with all new fields present
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{
                                  "latitude":33.6844,
                                  "longitude":73.0479,
                                  "clientTimestamp":"2026-06-19T10:00:00Z",
                                  "deviceInfo":"Pixel 8",
                                  "horizontalAccuracyM":5.2,
                                  "speedMps":1.3,
                                  "bearingDeg":270.0,
                                  "altitudeM":510.5,
                                  "batteryPercent":87
                                }]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        MetadataSample stored = metadataSamples.latest(recId).orElseThrow();
        assertThat(stored.horizontalAccuracyM()).isEqualTo(5.2);
        assertThat(stored.speedMps()).isEqualTo(1.3);
        assertThat(stored.bearingDeg()).isEqualTo(270.0);
        assertThat(stored.altitudeM()).isEqualTo(510.5);
        assertThat(stored.batteryPercent()).isEqualTo(87);

        // The public watch endpoint surfaces them in latestSample
        mvc.perform(get("/api/public/watch/" + recId + "?t=" + viewSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestSample.horizontalAccuracyM").value(5.2))
                .andExpect(jsonPath("$.latestSample.speedMps").value(1.3))
                .andExpect(jsonPath("$.latestSample.bearingDeg").value(270.0))
                .andExpect(jsonPath("$.latestSample.altitudeM").value(510.5))
                .andExpect(jsonPath("$.latestSample.batteryPercent").value(87));
    }

    @Test
    void samplesWithoutResponderFieldsBackwardCompatible() throws Exception {
        // Old Android client sends only the original 4 fields — must still be accepted
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{
                                  "latitude":33.6844,
                                  "longitude":73.0479,
                                  "clientTimestamp":"2026-06-19T10:00:01Z",
                                  "deviceInfo":"Old Phone"
                                }]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        MetadataSample stored = metadataSamples.latest(recId).orElseThrow();
        assertThat(stored.horizontalAccuracyM()).isNull();
        assertThat(stored.speedMps()).isNull();
        assertThat(stored.bearingDeg()).isNull();
        assertThat(stored.altitudeM()).isNull();
        assertThat(stored.batteryPercent()).isNull();

        // watch endpoint returns nulls for absent fields (no 500, no missing keys)
        mvc.perform(get("/api/public/watch/" + recId + "?t=" + viewSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestSample.latitude").exists())
                .andExpect(jsonPath("$.latestSample.horizontalAccuracyM").doesNotExist());
    }

    @Test
    void batteryAndBearingValidationRejectsOutOfRange() throws Exception {
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{
                                  "clientTimestamp":"2026-06-19T10:00:02Z",
                                  "batteryPercent":150
                                }]}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{
                                  "clientTimestamp":"2026-06-19T10:00:03Z",
                                  "bearingDeg":400.0
                                }]}"""))
                .andExpect(status().isBadRequest());
    }
}
