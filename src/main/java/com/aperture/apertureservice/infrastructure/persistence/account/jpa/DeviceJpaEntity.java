package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
class DeviceJpaEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false)
    String name;

    @Column(name = "token_hash", nullable = false, unique = true)
    String tokenHash;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    Instant lastSeenAt;

    @Column(name = "revoked_at")
    Instant revokedAt;

    protected DeviceJpaEntity() {}

    static DeviceJpaEntity from(Device d) {
        DeviceJpaEntity e = new DeviceJpaEntity();
        e.id = d.id();
        e.userId = d.userId();
        e.name = d.name();
        e.tokenHash = d.tokenHash();
        e.createdAt = d.createdAt();
        e.lastSeenAt = d.lastSeenAt();
        e.revokedAt = d.revokedAt();
        return e;
    }

    Device toDomain() {
        return new Device(id, userId, name, tokenHash, createdAt, lastSeenAt, revokedAt);
    }
}
