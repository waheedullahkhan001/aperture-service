package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hashed) {
        return encoder.matches(raw, hashed);
    }
}
