package com.aperture.apertureservice.domain.account;

import java.time.Instant;
import java.util.UUID;

public record Device(UUID id, UUID userId, String name, String tokenHash,
                     Instant createdAt, Instant lastSeenAt, Instant revokedAt) {

    public boolean revoked() {
        return revokedAt != null;
    }

    public Device seen(Instant now) {
        return new Device(id, userId, name, tokenHash, createdAt, now, revokedAt);
    }

    public Device revoke(Instant now) {
        return new Device(id, userId, name, tokenHash, createdAt, lastSeenAt, now);
    }
}
