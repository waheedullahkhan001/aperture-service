package com.aperture.apertureservice.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-endpoint token-bucket limits for unauthenticated auth paths.
 * Capacity is also the refill rate (tokens per minute).  Set a value to
 * a very large number (e.g. 1_000_000) to effectively disable a limit.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        long loginPerMinute,
        long registerPerMinute,
        long passwordResetRequestPerMinute,
        long resendVerificationPerMinute) {
}
