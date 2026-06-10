package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.User;

import java.util.UUID;

public interface ChangeProfile {
    User changeFullname(UUID userId, String fullname);
}
