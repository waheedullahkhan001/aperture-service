package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies per-IP rate limiting on auth endpoints.
 *
 * <p>Uses its own Spring context (separate from the main @IntegrationTest pool) with a limit
 * of 2 requests per minute on all rate-limited paths so the bucket is exhausted quickly
 * without needing to wait for refills.
 *
 * <p>Each test method uses a distinct {@code X-Forwarded-For} IP so that one test's exhausted
 * bucket does not bleed into another (buckets do not refill within the test run duration).
 */
@SpringBootTest(properties = {
        "app.rate-limit.login-per-minute=2",
        "app.rate-limit.register-per-minute=2",
        "app.rate-limit.password-reset-request-per-minute=2",
        "app.rate-limit.resend-verification-per-minute=2"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthRateLimitTest {

    @Autowired
    MockMvc mvc;

    // ---------------------------------------------------------------------------
    // Login endpoint — uses IPs in the 10.1.x.x range
    // ---------------------------------------------------------------------------

    @Test
    void loginExhaustedBucketReturns429WithCode() throws Exception {
        String ip    = "10.1.0.1";
        String body  = """
                {"email":"x@example.com","password":"wrong!!!1"}""";

        // First two requests consume the bucket (domain 401 — INVALID_CREDENTIALS, not 429)
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // Third request exceeds the per-minute limit -> 429
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void differentIpIsNotAffectedByExhaustedBucket() throws Exception {
        String exhaustedIp = "10.1.0.2";
        String otherIp     = "10.1.0.3";
        String body        = """
                {"email":"y@example.com","password":"wrong!!!1"}""";

        // Exhaust the bucket for exhaustedIp
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", exhaustedIp).content(body));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", exhaustedIp).content(body));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", exhaustedIp).content(body))
                .andExpect(status().isTooManyRequests());

        // A different IP still gets a domain 401 (not a rate-limit 429)
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", otherIp).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registerEndpointIsAlsoRateLimited() throws Exception {
        String ip   = "10.2.0.1";
        String body = """
                {"email":"ratelimit-reg@example.com","fullname":"Z","password":"abcdef1!"}""";

        // First two: succeed (202) or domain-conflict (409) — not 429
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body));

        // Third request: 429
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .header("X-Forwarded-For", ip).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void spoofedLeftmostForwardedForCannotBypassLimit() throws Exception {
        // nginx appends the real peer as the LAST X-Forwarded-For entry. Here the client
        // rotates a spoofed leftmost value each request while the real (rightmost) IP stays
        // constant — the limiter must key on the rightmost, so the limit still trips.
        // (With the old leftmost logic each request would land in a fresh bucket and never 429.)
        String realIp = "10.3.0.7";
        String body   = """
                {"email":"spoof@example.com","password":"wrong!!!1"}""";

        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", "1.1.1.1, " + realIp).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", "2.2.2.2, " + realIp).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .header("X-Forwarded-For", "3.3.3.3, " + realIp).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
