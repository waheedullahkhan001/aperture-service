package com.aperture.apertureservice.domain.account.api;

import java.util.UUID;

public interface LogOut {
    void logOut(UUID sessionId);
}
