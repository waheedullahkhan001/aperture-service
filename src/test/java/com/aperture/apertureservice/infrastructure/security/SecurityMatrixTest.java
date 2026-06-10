package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.api.LogIn;
import com.aperture.apertureservice.domain.account.api.RevokeDevice;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class SecurityMatrixTest {

    @Autowired MockMvc mvc;
    @Autowired Users users;
    @Autowired PasswordHasher hasher;
    @Autowired LogIn logIn;
    @Autowired ConnectDevice connectDevice;
    @Autowired RevokeDevice revokeDevice;

    String email;
    UUID userId;

    @BeforeEach
    void seed() {
        email = "sec-" + UUID.randomUUID() + "@example.com";
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email(email), "Sec",
                new HashedPassword(hasher.hash("abcdef1!")), true, Instant.now());
        users.save(u);
        userId = u.id();
    }

    @Test
    void unauthenticatedAndBadTokensAreRejectedWithCodes() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer apd_garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));
    }

    @Test
    void tokenTypesDoNotCross() throws Exception {
        String jwt = logIn.logIn(email, "abcdef1!", "test").accessToken();
        String deviceToken = connectDevice.connect(userId, "Pixel").token();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());
        // device token on a web endpoint -> rejected
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isUnauthorized());
        // jwt on a device endpoint -> rejected
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedDeviceGetsDistinctCode() throws Exception {
        MintedDevice m = connectDevice.connect(userId, "Pixel");
        revokeDevice.revoke(userId, m.id());
        mvc.perform(get("/api/v1/device/me").header("Authorization", "Bearer " + m.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE_REVOKED"));
    }

    @Test
    void publicSurfacesAreOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("""
                                {"email":"%s","password":"wrong!!!1"}""".formatted(email)))
                .andExpect(status().isUnauthorized())   // reached the controller: domain 401, not gateway 401
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized()); // needs shared secret
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer dev-webhook-secret-change-me"))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightAllowedInNonProd() throws Exception {
        mvc.perform(options("/api/public/watch/" + UUID.randomUUID())
                        .header("Origin", "http://example.org")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
