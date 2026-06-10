package com.aperture.apertureservice.domain.emergency.api;

import java.util.UUID;

public interface RemoveEmergencyContact {
    void remove(UUID userId, Long contactId);
}
