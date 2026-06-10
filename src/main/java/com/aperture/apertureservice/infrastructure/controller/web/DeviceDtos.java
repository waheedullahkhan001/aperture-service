package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {
    private DeviceDtos() {}

    public record ConnectRequest(@NotBlank @Size(max = 120) String name) {}

    public record MintedResponse(UUID id, String name, String token) {}

    public record DeviceResponse(UUID id, String name, Instant createdAt, Instant lastSeenAt, boolean revoked) {
        public static DeviceResponse from(Device d) {
            return new DeviceResponse(d.id(), d.name(), d.createdAt(), d.lastSeenAt(), d.revoked());
        }
    }
}
