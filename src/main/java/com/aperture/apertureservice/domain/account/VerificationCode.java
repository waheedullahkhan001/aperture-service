package com.aperture.apertureservice.domain.account;

import java.time.Instant;
import java.util.UUID;

public record VerificationCode(UUID userId, Purpose purpose, String codeHash,
                               Instant expiresAt, int attempts, Instant lastSentAt) {

    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }

    public VerificationCode withAttempt() {
        return new VerificationCode(userId, purpose, codeHash, expiresAt, attempts + 1, lastSentAt);
    }
}
