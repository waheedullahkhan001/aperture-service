package com.aperture.apertureservice.domain.emergency.api;

import com.aperture.apertureservice.domain.emergency.EmergencyContact;

import java.util.List;
import java.util.UUID;

public interface ListEmergencyContacts {
    List<EmergencyContact> list(UUID userId);
}
