package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.DeviceIdentity;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryDevices;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryDevices devices = new InMemoryDevices();
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final DeviceService service = new DeviceService(users, devices, tokens, clock);

    private User seed() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "Owner",
                new HashedPassword("h"), true, T0);
        users.save(u);
        return u;
    }

    @Test
    void connectMintsTokenStoredOnlyAsHash() {
        User u = seed();
        MintedDevice minted = service.connect(u.id(), "Pixel 8");

        assertThat(minted.token()).startsWith("apd_");
        com.aperture.apertureservice.domain.account.Device d = devices.byId(minted.id()).orElseThrow();
        assertThat(d.tokenHash()).isEqualTo(tokens.hash(minted.token()));
        assertThat(d.name()).isEqualTo("Pixel 8");
        assertThat(d.revoked()).isFalse();
    }

    @Test
    void identifyReturnsIdentityAndTouchesLastSeen() {
        User u = seed();
        MintedDevice minted = service.connect(u.id(), "Pixel 8");
        Clock later = Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC);
        DeviceService laterService = new DeviceService(users, devices, tokens, later);

        DeviceIdentity id = laterService.identify(minted.token());
        assertThat(id.userId()).isEqualTo(u.id());
        assertThat(id.deviceName()).isEqualTo("Pixel 8");
        assertThat(id.userFullname()).isEqualTo("Owner");
        assertThat(devices.byId(minted.id()).orElseThrow().lastSeenAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void identifyRejectsUnknownToken() {
        assertThatThrownBy(() -> service.identify("apd_nope"))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "INVALID_DEVICE_TOKEN");
    }

    @Test
    void identifyRejectsRevokedTokenWithDistinctCode() {
        User u = seed();
        MintedDevice minted = service.connect(u.id(), "Pixel 8");
        service.revoke(u.id(), minted.id());
        assertThatThrownBy(() -> service.identify(minted.token()))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "DEVICE_REVOKED");
    }

    @Test
    void revokeChecksOwnership() {
        User u = seed();
        MintedDevice minted = service.connect(u.id(), "Pixel 8");
        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), minted.id()))
                .isInstanceOf(NotFound.class);
    }

    @Test
    void listReturnsUsersDevices() {
        User u = seed();
        service.connect(u.id(), "Pixel 8");
        service.connect(u.id(), "Tablet");
        assertThat(service.list(u.id())).hasSize(2);
        assertThat(service.list(UUID.randomUUID())).isEmpty();
    }

    @Test
    void connectValidatesNameAndOwner() {
        User u = seed();
        assertThatThrownBy(() -> service.connect(u.id(), "  "))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "DEVICE_NAME_REQUIRED");
        assertThatThrownBy(() -> service.connect(UUID.randomUUID(), "Pixel"))
                .isInstanceOf(NotFound.class).hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
        assertThat(service.connect(u.id(), "  Pixel 8  ").name()).isEqualTo("Pixel 8"); // trimmed
    }

    @Test
    void doubleRevokeKeepsOriginalTimestamp() {
        User u = seed();
        MintedDevice m = service.connect(u.id(), "Pixel 8");
        service.revoke(u.id(), m.id());
        Instant first = devices.byId(m.id()).orElseThrow().revokedAt();
        DeviceService later = new DeviceService(users, devices, tokens,
                Clock.fixed(T0.plusSeconds(120), ZoneOffset.UTC));
        later.revoke(u.id(), m.id());
        assertThat(devices.byId(m.id()).orElseThrow().revokedAt()).isEqualTo(first);
    }
}
