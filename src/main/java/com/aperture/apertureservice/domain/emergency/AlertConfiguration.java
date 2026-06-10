package com.aperture.apertureservice.domain.emergency;

import java.util.UUID;

public record AlertConfiguration(UUID userId, int countdownDurationSeconds, String messageTemplate) {

    public static final String DEFAULT_TEMPLATE =
            "I may be in an emergency. Live stream: {{streamUrl}}";

    public static AlertConfiguration defaults(UUID userId) {
        return new AlertConfiguration(userId, 30, DEFAULT_TEMPLATE);
    }
}
