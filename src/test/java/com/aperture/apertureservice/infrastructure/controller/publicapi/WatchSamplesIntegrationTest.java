package com.aperture.apertureservice.infrastructure.controller.publicapi;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
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
class WatchSamplesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Users users;
    @Autowired ConnectDevice connectDevice;
    @Autowired PasswordHasher passwordHasher;

    UUID recId;
    String deviceToken;
    String viewSecret;

    @BeforeEach
    void seed() throws Exception {
        String email = "watchsamples-" + UUID.randomUUID() + "@example.com";
        User u = new User(UuidCreator.getTimeOrderedEpoch(),
                new Email(email), "Sample Owner",
                new HashedPassword(passwordHasher.hash("Passw0rd!")), true, Instant.now());
        users.save(u);

        MintedDevice m = connectDevice.connect(u.id(), "TestPhone");
        deviceToken = m.token();

        recId = UuidCreator.getTimeOrderedEpoch();
        MvcResult ensureResult = mvc.perform(put("/api/v1/device/recordings/" + recId)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(ensureResult.getResponse().getContentAsString());
        String watchUrl = body.get("watchUrl").asText();
        viewSecret = watchUrl.substring(watchUrl.indexOf("?t=") + 3);
    }

    @Test
    void watchResponseIncludesSamplesArrayEmptyWhenNoSamplesExist() throws Exception {
        mvc.perform(get("/api/public/watch/{id}", recId).param("t", viewSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples").isArray())
                .andExpect(jsonPath("$.samples.length()").value(0));
    }

    @Test
    void watchResponseIncludesSamplesChronologicallyWithAllFields() throws Exception {
        // Post two samples out of order — older second
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[
                                  {
                                    "latitude":33.6844,
                                    "longitude":73.0479,
                                    "clientTimestamp":"2026-06-21T10:01:00Z",
                                    "deviceInfo":"Pixel 8",
                                    "horizontalAccuracyM":4.5,
                                    "speedMps":2.1,
                                    "bearingDeg":90.0,
                                    "altitudeM":520.0,
                                    "batteryPercent":76
                                  },
                                  {
                                    "latitude":33.6800,
                                    "longitude":73.0400,
                                    "clientTimestamp":"2026-06-21T10:00:00Z",
                                    "deviceInfo":"Pixel 8",
                                    "horizontalAccuracyM":5.0,
                                    "speedMps":0.0,
                                    "bearingDeg":0.0,
                                    "altitudeM":510.0,
                                    "batteryPercent":80
                                  }
                                ]}"""))
                .andExpect(status().isAccepted());

        MvcResult result = mvc.perform(get("/api/public/watch/{id}", recId).param("t", viewSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples").isArray())
                .andExpect(jsonPath("$.samples.length()").value(2))
                // latestSample still present
                .andExpect(jsonPath("$.latestSample").exists())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        JsonNode samples = body.get("samples");

        // chronological order: older first
        assertThat(samples.get(0).get("clientTimestamp").asText()).isEqualTo("2026-06-21T10:00:00Z");
        assertThat(samples.get(1).get("clientTimestamp").asText()).isEqualTo("2026-06-21T10:01:00Z");

        // all telemetry fields present in first sample
        JsonNode first = samples.get(0);
        assertThat(first.get("latitude").asDouble()).isEqualTo(33.6800);
        assertThat(first.get("longitude").asDouble()).isEqualTo(73.0400);
        assertThat(first.get("deviceInfo").asText()).isEqualTo("Pixel 8");
        assertThat(first.get("horizontalAccuracyM").asDouble()).isEqualTo(5.0);
        assertThat(first.get("speedMps").asDouble()).isEqualTo(0.0);
        assertThat(first.get("bearingDeg").asDouble()).isEqualTo(0.0);
        assertThat(first.get("altitudeM").asDouble()).isEqualTo(510.0);
        assertThat(first.get("batteryPercent").asInt()).isEqualTo(80);

        // no internal id or recordingId in the public DTO
        assertThat(first.has("id")).isFalse();
        assertThat(first.has("recordingId")).isFalse();
        assertThat(first.has("serverReceivedAt")).isFalse();
    }

    @Test
    void watchResponseWrongTokenReturns403EvenWhenSamplesExist() throws Exception {
        // Add samples to the recording
        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType("application/json")
                        .content("""
                                {"samples":[{
                                  "latitude":33.6844,
                                  "longitude":73.0479,
                                  "clientTimestamp":"2026-06-21T11:00:00Z",
                                  "deviceInfo":"Pixel"
                                }]}"""))
                .andExpect(status().isAccepted());

        // Wrong token still 403 — auth is unchanged by samples presence
        mvc.perform(get("/api/public/watch/{id}", recId).param("t", "apv_wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void watchResponseUnknownRecordingReturns404() throws Exception {
        mvc.perform(get("/api/public/watch/{id}", UUID.randomUUID()).param("t", "apv_anything"))
                .andExpect(status().isNotFound());
    }
}
