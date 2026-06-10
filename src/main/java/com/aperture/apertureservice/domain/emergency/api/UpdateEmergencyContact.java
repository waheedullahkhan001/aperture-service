package com.aperture.apertureservice.domain.emergency.api;

import com.aperture.apertureservice.domain.emergency.EmergencyContact;

import java.util.UUID;

public interface UpdateEmergencyContact {
    EmergencyContact update(UUID userId, Long contactId, String name, String email, String messageOverride);
}
