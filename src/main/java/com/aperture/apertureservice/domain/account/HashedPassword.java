package com.aperture.apertureservice.domain.account;

public record HashedPassword(String value) {
    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hash required");
        }
    }
}
