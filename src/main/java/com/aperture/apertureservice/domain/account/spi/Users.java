package com.aperture.apertureservice.domain.account.spi;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.User;

import java.util.Optional;
import java.util.UUID;

public interface Users {
    Optional<User> byId(UUID id);
    Optional<User> byEmail(Email email);
    void save(User user);
    void delete(UUID userId);
}
