package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String fullname;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(nullable = false)
    boolean verified;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected UserJpaEntity() {}

    static UserJpaEntity from(User u) {
        UserJpaEntity e = new UserJpaEntity();
        e.id = u.id();
        e.email = u.email().value();
        e.fullname = u.fullname();
        e.passwordHash = u.passwordHash().value();
        e.verified = u.verified();
        e.createdAt = u.createdAt();
        return e;
    }

    User toDomain() {
        return new User(id, new Email(email), fullname, new HashedPassword(passwordHash), verified, createdAt);
    }
}
