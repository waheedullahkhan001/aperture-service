package com.aperture.apertureservice.domain.account.api;

import java.util.UUID;

public interface RevokeDevice {
    void revoke(UUID userId, UUID deviceId);
}
