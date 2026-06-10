package com.aperture.apertureservice.domain.emergency.spi;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyContacts {
    List<EmergencyContact> byUser(UUID userId);
    Optional<EmergencyContact> byId(Long id);
    int countByUser(UUID userId);
    boolean existsByUserAndEmail(UUID userId, Email email);
    EmergencyContact save(EmergencyContact contact);
    void delete(Long id);
}
