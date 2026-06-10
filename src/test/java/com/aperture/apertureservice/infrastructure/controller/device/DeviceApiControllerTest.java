package com.aperture.apertureservice.infrastructure.controller.device;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.recording.EnsureResult;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.api.AppendMetadataSamples;
import com.aperture.apertureservice.domain.recording.api.EndRecording;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.infrastructure.configuration.AppProperties;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedDevice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DeviceApiControllerTest.Props.class)
class DeviceApiControllerTest {

    @TestConfiguration
    static class Props {
        @Bean AppProperties appProperties() {
            return new AppProperties("http://localhost", "/tmp", "secret",
                    new AppProperties.Jwt("x", Duration.ofMinutes(15)),
                    new AppProperties.Session(Duration.ofDays(30)),
                    new AppProperties.Streaming("http://localhost:8888", "http://localhost:8889"),
                    new AppProperties.Schedule(Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60)));
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean EnsureRecording ensureRecording;
    @MockitoBean EndRecording endRecording;
    @MockitoBean AppendMetadataSamples appendSamples;
    @MockitoBean GetAlertConfiguration getAlertConfig;
    @MockitoBean ListEmergencyContacts listContacts;

    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID recId = UUID.randomUUID();

    private Authentication asDevice() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedDevice(userId, deviceId, "Pixel 8", "Owner"), null,
                List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
    }

    @Test
    void deviceMeEchoesIdentityFromPrincipal() throws Exception {
        mvc.perform(get("/api/v1/device/me").principal(asDevice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userFullname").value("Owner"))
                .andExpect(jsonPath("$.deviceName").value("Pixel 8"));
    }

    @Test
    void ensureReturns201OnCreateAnd200OnExisting() throws Exception {
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        Recording r = new Recording(recId, userId, RecordingStatus.PENDING, t, null, "apv_s",
                t.plusSeconds(30), null);
        when(ensureRecording.ensure(recId, userId, t)).thenReturn(new EnsureResult(r, true));

        mvc.perform(put("/api/v1/device/recordings/" + recId).principal(asDevice())
                        .contentType("application/json")
                        .content("""
                                {"startedAt":"2026-06-07T12:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordingId").value(recId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.watchUrl").value("http://localhost/watch/" + recId + "?t=apv_s"));

        when(ensureRecording.ensure(recId, userId, t)).thenReturn(new EnsureResult(r, false));
        mvc.perform(put("/api/v1/device/recordings/" + recId).principal(asDevice())
                        .contentType("application/json")
                        .content("""
                                {"startedAt":"2026-06-07T12:00:00Z"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void ensureWithEmptyBodyWorks() throws Exception {
        Recording r = new Recording(recId, userId, RecordingStatus.PENDING, Instant.now(), null, "apv_s",
                null, null);
        when(ensureRecording.ensure(recId, userId, null)).thenReturn(new EnsureResult(r, true));
        mvc.perform(put("/api/v1/device/recordings/" + recId).principal(asDevice()))
                .andExpect(status().isCreated());
    }

    @Test
    void endSamplesAndAlertConfig() throws Exception {
        when(appendSamples.append(eq(recId), eq(userId), anyList())).thenReturn(1);
        when(getAlertConfig.get(userId)).thenReturn(new AlertConfiguration(userId, 15, "t {{streamUrl}}"));
        when(listContacts.list(userId)).thenReturn(List.of(
                new EmergencyContact(1L, userId, "Mom", new Email("mom@example.com"), null)));

        mvc.perform(post("/api/v1/device/recordings/" + recId + "/end").principal(asDevice()))
                .andExpect(status().isNoContent());
        verify(endRecording).end(recId, userId);

        mvc.perform(post("/api/v1/device/recordings/" + recId + "/metadata-samples").principal(asDevice())
                        .contentType("application/json")
                        .content("""
                                {"samples":[{"latitude":1.5,"longitude":2.5,
                                  "clientTimestamp":"2026-06-07T12:00:00Z","deviceInfo":"Pixel"}]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        mvc.perform(get("/api/v1/device/alert-config").principal(asDevice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countdownDurationSeconds").value(15))
                .andExpect(jsonPath("$.hasContacts").value(true));
    }
}
