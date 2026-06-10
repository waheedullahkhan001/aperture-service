package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.Device;
import com.aperture.apertureservice.domain.account.DeviceIdentity;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import com.aperture.apertureservice.domain.account.api.ListDevices;
import com.aperture.apertureservice.domain.account.api.RevokeDevice;
import com.aperture.apertureservice.domain.account.spi.Devices;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DomainService
public class DeviceService implements ConnectDevice, ListDevices, RevokeDevice, IdentifyDevice {

    private final Users users;
    private final Devices devices;
    private final RandomTokens tokens;
    private final Clock clock;

    public DeviceService(Users users, Devices devices, RandomTokens tokens, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MintedDevice connect(UUID userId, String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequest("DEVICE_NAME_REQUIRED", "Device name is required");
        }
        Instant now = clock.instant();
        String token = tokens.token("apd_");
        Device device = new Device(UuidCreator.getTimeOrderedEpoch(), userId, name.trim(),
                tokens.hash(token), now, now, null);
        devices.save(device);
        return new MintedDevice(device.id(), device.name(), token);
    }

    @Override
    public List<Device> list(UUID userId) {
        return devices.byUser(userId);
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID deviceId) {
        Device d = devices.byId(deviceId)
                .filter(x -> x.userId().equals(userId))
                .orElseThrow(() -> new NotFound("DEVICE_NOT_FOUND", "Device not found"));
        devices.save(d.revoke(clock.instant()));
    }

    @Override
    @Transactional
    public DeviceIdentity identify(String deviceToken) {
        Device d = devices.byTokenHash(tokens.hash(deviceToken))
                .orElseThrow(() -> new Unauthorized("INVALID_DEVICE_TOKEN", "Invalid device token"));
        if (d.revoked()) {
            throw new Unauthorized("DEVICE_REVOKED", "Device has been revoked");
        }
        devices.save(d.seen(clock.instant()));
        User owner = users.byId(d.userId())
                .orElseThrow(() -> new Unauthorized("INVALID_DEVICE_TOKEN", "Invalid device token"));
        return new DeviceIdentity(d.id(), d.userId(), d.name(), owner.fullname());
    }
}
