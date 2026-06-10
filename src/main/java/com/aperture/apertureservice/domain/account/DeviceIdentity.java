package com.aperture.apertureservice.domain.account;

import java.util.UUID;

public record DeviceIdentity(UUID deviceId, UUID userId, String deviceName, String userFullname) {}
