package com.aperture.apertureservice.domain.account;

import java.util.UUID;

public record AccessClaims(UUID userId, UUID sessionId) {}
