package com.aperture.apertureservice.domain.account.spi;

import com.aperture.apertureservice.domain.account.VerificationCode;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodes {
    Optional<VerificationCode> find(UUID userId, VerificationCode.Purpose purpose);
    void save(VerificationCode code);
    void delete(UUID userId, VerificationCode.Purpose purpose);
}
