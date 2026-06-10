package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.Users;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaUsers implements Users {

    private final UserJpaRepository repo;

    JpaUsers(UserJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<User> byId(UUID id) {
        return repo.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> byEmail(Email email) {
        return repo.findByEmail(email.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    public void save(User user) {
        repo.save(UserJpaEntity.from(user));
    }

    @Override
    public void delete(UUID userId) {
        repo.deleteById(userId);
    }
}
