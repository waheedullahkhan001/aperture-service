package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.spi.VerificationCodes;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaVerificationCodes implements VerificationCodes {

    private final VerificationCodeJpaRepository repo;

    JpaVerificationCodes(VerificationCodeJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<VerificationCode> find(UUID userId, VerificationCode.Purpose purpose) {
        return repo.findById(new VerificationCodeId(userId, purpose.name()))
                .map(VerificationCodeJpaEntity::toDomain);
    }

    @Override
    public void save(VerificationCode code) {
        repo.save(VerificationCodeJpaEntity.from(code));
    }

    @Override
    public void delete(UUID userId, VerificationCode.Purpose purpose) {
        repo.deleteById(new VerificationCodeId(userId, purpose.name()));
    }
}
