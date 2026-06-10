package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.spi.AccountCleanup;

import java.util.UUID;

@Stub
public class NoopAccountCleanup implements AccountCleanup {
    @Override
    public void purgeUserData(UUID userId) {}
}
