package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record ProfileResponse(UUID id, String email, String fullname, boolean verified, Instant createdAt) {
        public static ProfileResponse from(User u) {
            return new ProfileResponse(u.id(), u.email().value(), u.fullname(), u.verified(), u.createdAt());
        }
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullname) {}

    public record SessionResponse(UUID id, String label, Instant issuedAt, Instant lastUsedAt, Instant expiresAt) {
        public static SessionResponse from(Session s) {
            return new SessionResponse(s.id(), s.label(), s.issuedAt(), s.lastUsedAt(), s.expiresAt());
        }
    }
}
