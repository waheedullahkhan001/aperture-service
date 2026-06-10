package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.Device;
import com.aperture.apertureservice.domain.account.spi.Devices;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemoryDevices implements Devices {
    private final Map<UUID, Device> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<Device> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Device> byTokenHash(String tokenHash) {
        return byId.values().stream().filter(d -> d.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public List<Device> byUser(UUID userId) {
        return byId.values().stream().filter(d -> d.userId().equals(userId)).toList();
    }

    @Override
    public void save(Device device) {
        byId.put(device.id(), device);
    }
}
