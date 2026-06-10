package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.VerificationCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_codes")
@IdClass(VerificationCodeId.class)
class VerificationCodeJpaEntity {

    @Id
    @Column(name = "user_id")
    UUID userId;

    @Id
    String purpose;

    @Column(name = "code_hash", nullable = false)
    String codeHash;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    int attempts;

    @Column(name = "last_sent_at", nullable = false)
    Instant lastSentAt;

    protected VerificationCodeJpaEntity() {}

    static VerificationCodeJpaEntity from(VerificationCode c) {
        VerificationCodeJpaEntity e = new VerificationCodeJpaEntity();
        e.userId = c.userId();
        e.purpose = c.purpose().name();
        e.codeHash = c.codeHash();
        e.expiresAt = c.expiresAt();
        e.attempts = c.attempts();
        e.lastSentAt = c.lastSentAt();
        return e;
    }

    VerificationCode toDomain() {
        return new VerificationCode(userId, VerificationCode.Purpose.valueOf(purpose), codeHash,
                expiresAt, attempts, lastSentAt);
    }
}
