package com.aperture.apertureservice.domain.account.spi;

public interface PasswordHasher {
    String hash(String raw);
    boolean matches(String raw, String hashed);
}
