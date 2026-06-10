package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;

@Stub
public class FakePasswordHasher implements PasswordHasher {
    @Override
    public String hash(String raw) {
        return "h(" + raw + ")";
    }

    @Override
    public boolean matches(String raw, String hashed) {
        return hashed.equals(hash(raw));
    }
}
