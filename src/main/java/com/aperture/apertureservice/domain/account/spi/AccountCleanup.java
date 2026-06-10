package com.aperture.apertureservice.domain.account.spi;

import java.util.UUID;

public interface AccountCleanup {
    void purgeUserData(UUID userId);
}
