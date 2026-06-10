package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class VerificationCodeId implements Serializable {

    UUID userId;
    String purpose;

    VerificationCodeId() {}

    VerificationCodeId(UUID userId, String purpose) {
        this.userId = userId;
        this.purpose = purpose;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VerificationCodeId other
                && Objects.equals(userId, other.userId)
                && Objects.equals(purpose, other.purpose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, purpose);
    }
}
