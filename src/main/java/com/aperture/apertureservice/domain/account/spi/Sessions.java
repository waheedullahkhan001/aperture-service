package com.aperture.apertureservice.domain.account.spi;

import com.aperture.apertureservice.domain.account.Session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Sessions {
    Optional<Session> byId(UUID id);
    Optional<Session> byRefreshTokenHash(String hash);
    Optional<Session> byPreviousTokenHash(String hash);
    List<Session> byUser(UUID userId);
    void save(Session session);
    void delete(UUID sessionId);
    void deleteAllForUser(UUID userId);
}
