package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.spi.OtpGenerator;

import java.security.SecureRandom;

public class SecureOtpGenerator implements OtpGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String sixDigits() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
