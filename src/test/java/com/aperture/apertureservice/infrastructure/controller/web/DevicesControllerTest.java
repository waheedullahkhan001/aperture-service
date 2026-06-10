package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.Device;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.api.ListDevices;
import com.aperture.apertureservice.domain.account.api.RevokeDevice;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DevicesController.class)
@AutoConfigureMockMvc(addFilters = false)
class DevicesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConnectDevice connectDevice;
    @MockitoBean ListDevices listDevices;
    @MockitoBean RevokeDevice revokeDevice;

    private final UUID userId = UUID.randomUUID();

    private Authentication asUser() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void mintReturnsTokenOnce() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(connectDevice.connect(userId, "Pixel 8"))
                .thenReturn(new MintedDevice(deviceId, "Pixel 8", "apd_secret"));
        mvc.perform(post("/api/v1/me/devices").principal(asUser()).contentType("application/json")
                        .content("""
                                {"name":"Pixel 8"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("apd_secret"));
    }

    @Test
    void listAndRevoke() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        when(listDevices.list(userId)).thenReturn(List.of(
                new Device(deviceId, userId, "Pixel 8", "#h", t, t, null)));
        mvc.perform(get("/api/v1/me/devices").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pixel 8"))
                .andExpect(jsonPath("$[0].revoked").value(false));
        mvc.perform(delete("/api/v1/me/devices/" + deviceId).principal(asUser()))
                .andExpect(status().isNoContent());
        verify(revokeDevice).revoke(userId, deviceId);
    }
}
