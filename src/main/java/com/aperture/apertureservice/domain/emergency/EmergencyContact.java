package com.aperture.apertureservice.domain.emergency;

import com.aperture.apertureservice.domain.account.Email;

import java.util.UUID;

public record EmergencyContact(Long id, UUID userId, String name, Email email, String messageOverride) {}
