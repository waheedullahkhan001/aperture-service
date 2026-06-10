package com.aperture.apertureservice.domain.account;

import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UUID userId, String label, String refreshTokenHash,
                      String previousTokenHash, Instant issuedAt, Instant lastUsedAt, Instant expiresAt) {

    public Session rotated(String newHash, Instant now, Instant newExpiry) {
        return new Session(id, userId, label, newHash, refreshTokenHash, issuedAt, now, newExpiry);
    }
}
