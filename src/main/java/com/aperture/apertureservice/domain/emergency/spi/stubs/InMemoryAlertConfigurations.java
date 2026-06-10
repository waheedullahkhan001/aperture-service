package com.aperture.apertureservice.domain.emergency.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemoryAlertConfigurations implements AlertConfigurations {
    private final Map<UUID, AlertConfiguration> byUser = new ConcurrentHashMap<>();

    @Override
    public Optional<AlertConfiguration> byUser(UUID userId) {
        return Optional.ofNullable(byUser.get(userId));
    }

    @Override
    public void save(AlertConfiguration c) {
        byUser.put(c.userId(), c);
    }
}
