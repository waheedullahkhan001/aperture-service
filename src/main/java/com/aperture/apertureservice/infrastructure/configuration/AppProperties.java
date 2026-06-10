package com.aperture.apertureservice.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicOrigin,
        String recordingsPath,
        String webhookSecret,
        Jwt jwt,
        Session session,
        Streaming streaming,
        Schedule schedule) {

    public record Jwt(String secret, Duration accessTtl) {}
    public record Session(Duration refreshTtl) {}
    public record Streaming(String hlsBase, String webrtcBase) {}
    public record Schedule(Duration dispatchDelay, Duration retryDelay, Duration sweepDelay) {}
}
