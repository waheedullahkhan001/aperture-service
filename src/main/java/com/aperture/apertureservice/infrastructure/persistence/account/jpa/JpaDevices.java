package com.aperture.apertureservice.infrastructure.persistence.account.jpa;

import com.aperture.apertureservice.domain.account.Device;
import com.aperture.apertureservice.domain.account.spi.Devices;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaDevices implements Devices {

    private final DeviceJpaRepository repo;

    JpaDevices(DeviceJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<Device> byId(UUID id) {
        return repo.findById(id).map(DeviceJpaEntity::toDomain);
    }

    @Override
    public Optional<Device> byTokenHash(String h) {
        return repo.findByTokenHash(h).map(DeviceJpaEntity::toDomain);
    }

    @Override
    public List<Device> byUser(UUID userId) {
        return repo.findByUserId(userId).stream().map(DeviceJpaEntity::toDomain).toList();
    }

    @Override
    public void save(Device d) {
        repo.save(DeviceJpaEntity.from(d));
    }
}
