package com.aperture.apertureservice.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSecretGuardTest {

    private AppProperties props(String jwtSecret, String webhookSecret) {
        return new AppProperties("https://x", "/data", webhookSecret,
                new AppProperties.Jwt(jwtSecret, Duration.ofMinutes(15)),
                new AppProperties.Session(Duration.ofDays(30)),
                new AppProperties.Streaming("h", "w"),
                new AppProperties.Schedule(Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60)),
                new AppProperties.MediaMtx("http://mediamtx:9996"));
    }

    @Test
    void refusesDevSentinels() {
        assertThatThrownBy(() -> new ProdSecretGuard(props(ProdSecretGuard.DEV_JWT_SENTINEL, "real")).check())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProdSecretGuard(props("cmVhbHNlY3JldA==", "dev-webhook-secret-change-me")).check())
                .isInstanceOf(IllegalStateException.class);
        new ProdSecretGuard(props("cmVhbHNlY3JldA==", "real-webhook-secret")).check(); // ok
    }
}
