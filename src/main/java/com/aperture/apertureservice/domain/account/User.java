package com.aperture.apertureservice.domain.account;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, Email email, String fullname, HashedPassword passwordHash,
                   boolean verified, Instant createdAt) {

    public User verifiedNow() {
        return new User(id, email, fullname, passwordHash, true, createdAt);
    }

    public User withFullname(String newFullname) {
        return new User(id, email, newFullname, passwordHash, verified, createdAt);
    }

    public User withPasswordHash(HashedPassword newHash) {
        return new User(id, email, fullname, newHash, verified, createdAt);
    }
}
