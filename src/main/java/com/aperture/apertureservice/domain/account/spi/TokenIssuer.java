package com.aperture.apertureservice.domain.account.spi;

import com.aperture.apertureservice.domain.account.AccessClaims;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface TokenIssuer {
    String issue(UUID userId, UUID sessionId, Duration ttl);
    Optional<AccessClaims> validate(String token);
}
