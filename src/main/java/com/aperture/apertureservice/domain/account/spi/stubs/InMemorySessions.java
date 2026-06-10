package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.spi.Sessions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemorySessions implements Sessions {
    private final Map<UUID, Session> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Session> byRefreshTokenHash(String hash) {
        return byId.values().stream().filter(s -> s.refreshTokenHash().equals(hash)).findFirst();
    }

    @Override
    public Optional<Session> byPreviousTokenHash(String hash) {
        return byId.values().stream().filter(s -> hash.equals(s.previousTokenHash())).findFirst();
    }

    @Override
    public List<Session> byUser(UUID userId) {
        return byId.values().stream().filter(s -> s.userId().equals(userId)).toList();
    }

    @Override
    public void save(Session session) {
        byId.put(session.id(), session);
    }

    @Override
    public void delete(UUID sessionId) {
        byId.remove(sessionId);
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        byId.values().removeIf(s -> s.userId().equals(userId));
    }
}
