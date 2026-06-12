package com.aperture.apertureservice.infrastructure.controller.device;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceApiDtos {
    private DeviceApiDtos() {}

    public record DeviceMeResponse(String userFullname, String deviceName) {}

    public record EnsureRequest(Instant startedAt) {}

    public record EnsureResponse(UUID recordingId, String status, Instant countdownEndsAt, String watchUrl) {}

    public record SampleDto(BigDecimal latitude, BigDecimal longitude,
                            @NotNull Instant clientTimestamp, @Size(max = 255) String deviceInfo) {}

    public record SamplesRequest(@NotNull @Size(max = 500) List<@Valid SampleDto> samples) {}

    public record AcceptedResponse(int accepted) {}

    public record DeviceAlertConfigResponse(int countdownDurationSeconds, boolean hasContacts) {}

    public record CancelAlertsResponse(boolean cancelled, boolean alertsAlreadyDispatched) {}
}
