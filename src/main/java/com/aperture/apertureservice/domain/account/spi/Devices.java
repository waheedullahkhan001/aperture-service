package com.aperture.apertureservice.domain.account.spi;

import com.aperture.apertureservice.domain.account.Device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Devices {
    Optional<Device> byId(UUID id);
    Optional<Device> byTokenHash(String tokenHash);
    List<Device> byUser(UUID userId);
    void save(Device device);
}
