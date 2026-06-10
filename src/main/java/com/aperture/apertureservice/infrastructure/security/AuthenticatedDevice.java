package com.aperture.apertureservice.infrastructure.security;

import java.util.UUID;

public record AuthenticatedDevice(UUID userId, UUID deviceId, String deviceName, String userFullname) {}
