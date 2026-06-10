package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.AccessClaims;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Stub
public class FakeTokenIssuer implements TokenIssuer {
    @Override
    public String issue(UUID userId, UUID sessionId, Duration ttl) {
        return "jwt." + userId + "." + sessionId;
    }

    @Override
    public Optional<AccessClaims> validate(String token) {
        if (!token.startsWith("jwt.")) return Optional.empty();
        String[] parts = token.split("\\.");
        return Optional.of(new AccessClaims(UUID.fromString(parts[1]), UUID.fromString(parts[2])));
    }
}
