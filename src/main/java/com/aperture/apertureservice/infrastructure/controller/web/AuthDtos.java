package com.aperture.apertureservice.infrastructure.controller.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@NotBlank @Size(max = 255) String email,
                                  @NotBlank @Size(max = 255) String fullname,
                                  @NotBlank @Size(max = 128) String password) {}

    public record VerifyEmailRequest(@NotBlank String email, @NotBlank String code) {}

    public record ResendRequest(@NotBlank String email) {}

    public record LoginRequest(@NotBlank String email, @NotBlank @Size(max = 128) String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ResetRequest(@NotBlank String email) {}

    public record ResetConfirmRequest(@NotBlank String email, @NotBlank String code,
                                      @NotBlank @Size(max = 128) String newPassword) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
}
