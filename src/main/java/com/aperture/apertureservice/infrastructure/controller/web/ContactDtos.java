package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ContactDtos {
    private ContactDtos() {}

    public record ContactRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Size(max = 255) String email,
                                 @Size(max = 2000) String messageOverride) {}

    public record ContactResponse(Long id, String name, String email, String messageOverride) {
        public static ContactResponse from(EmergencyContact c) {
            return new ContactResponse(c.id(), c.name(), c.email().value(), c.messageOverride());
        }
    }

    public record AlertConfigRequest(@NotNull @Min(0) @Max(3600) Integer countdownDurationSeconds,
                                     @NotBlank @Size(max = 4000) String messageTemplate) {}

    public record AlertConfigResponse(int countdownDurationSeconds, String messageTemplate) {
        public static AlertConfigResponse from(AlertConfiguration c) {
            return new AlertConfigResponse(c.countdownDurationSeconds(), c.messageTemplate());
        }
    }
}
