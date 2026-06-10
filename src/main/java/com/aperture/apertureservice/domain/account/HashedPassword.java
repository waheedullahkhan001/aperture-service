package com.aperture.apertureservice.domain.account;

public record HashedPassword(String value) {
    public HashedPassword {
        // IllegalArgumentException (not BadRequest) on purpose: this wraps the hasher's output, never user input — blank here is a programming bug.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hash required");
        }
    }
}
