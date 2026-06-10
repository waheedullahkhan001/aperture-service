package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.Users;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Fidelity gap vs the real adapter: no unique-email enforcement here. The DB has a UNIQUE constraint on email; a same-email race that this stub would let through fails at the constraint in production (surfacing as a 500 — accepted for MVP since register pre-checks byEmail).
@Stub
public class InMemoryUsers implements Users {
    private final Map<UUID, User> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<User> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> byEmail(Email email) {
        return byId.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public void save(User user) {
        byId.put(user.id(), user);
    }

    @Override
    public void delete(UUID userId) {
        byId.remove(userId);
    }
}
