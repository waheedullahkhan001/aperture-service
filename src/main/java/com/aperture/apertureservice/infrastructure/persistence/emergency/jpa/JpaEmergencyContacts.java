package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaEmergencyContacts implements EmergencyContacts {

    private final ContactJpaRepository repo;

    JpaEmergencyContacts(ContactJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<EmergencyContact> byUser(UUID userId) {
        return repo.findByUserIdOrderById(userId).stream().map(ContactJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<EmergencyContact> byId(Long id) {
        return repo.findById(id).map(ContactJpaEntity::toDomain);
    }

    @Override
    public int countByUser(UUID userId) {
        return repo.countByUserId(userId);
    }

    @Override
    public boolean existsByUserAndEmail(UUID userId, Email email) {
        return repo.existsByUserIdAndEmail(userId, email.value());
    }

    @Override
    public EmergencyContact save(EmergencyContact c) {
        return repo.save(ContactJpaEntity.from(c)).toDomain();
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
