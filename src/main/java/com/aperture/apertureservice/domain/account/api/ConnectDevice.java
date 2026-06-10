package com.aperture.apertureservice.domain.account.api;

import com.aperture.apertureservice.domain.account.MintedDevice;

import java.util.UUID;

public interface ConnectDevice {
    MintedDevice connect(UUID userId, String name);
}
