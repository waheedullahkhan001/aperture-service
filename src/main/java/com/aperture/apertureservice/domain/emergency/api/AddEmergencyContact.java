package com.aperture.apertureservice.domain.emergency.api;

import com.aperture.apertureservice.domain.emergency.EmergencyContact;

import java.util.UUID;

public interface AddEmergencyContact {
    EmergencyContact add(UUID userId, String name, String email, String messageOverride);
}
