package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
class SessionJpaEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false)
    String label;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    String refreshTokenHash;

    @Column(name = "previous_token_hash")
    String previousTokenHash;

    @Column(name = "issued_at", nullable = false)
    Instant issuedAt;

    @Column(name = "last_used_at", nullable = false)
    Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected SessionJpaEntity() {}

    static SessionJpaEntity from(Session s) {
        SessionJpaEntity e = new SessionJpaEntity();
        e.id = s.id();
        e.userId = s.userId();
        e.label = s.label();
        e.refreshTokenHash = s.refreshTokenHash();
        e.previousTokenHash = s.previousTokenHash();
        e.issuedAt = s.issuedAt();
        e.lastUsedAt = s.lastUsedAt();
        e.expiresAt = s.expiresAt();
        return e;
    }

    Session toDomain() {
        return new Session(id, userId, label, refreshTokenHash, previousTokenHash,
                issuedAt, lastUsedAt, expiresAt);
    }
}
