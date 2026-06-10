package com.aperture.apertureservice.domain.account.api;

import java.util.UUID;

public interface RevokeSession {
    void revoke(UUID userId, UUID sessionId);
}
