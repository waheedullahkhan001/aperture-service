package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.AccountCleanup;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemorySessions;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemorySessions sessions = new InMemorySessions();
    private final RecordingCleanup cleanup = new RecordingCleanup();
    private final ProfileService service = new ProfileService(users, sessions, cleanup);

    static class RecordingCleanup implements AccountCleanup {
        UUID purged;
        @Override public void purgeUserData(UUID userId) { purged = userId; }
    }

    private User seed() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "Old Name",
                new HashedPassword("h"), true, T0);
        users.save(u);
        return u;
    }

    @Test
    void getReturnsUserOr404() {
        User u = seed();
        assertThat(service.get(u.id())).isEqualTo(u);
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(NotFound.class).hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void changeFullnameValidatesAndSaves() {
        User u = seed();
        assertThat(service.changeFullname(u.id(), "New Name").fullname()).isEqualTo("New Name");
        assertThatThrownBy(() -> service.changeFullname(u.id(), "  "))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "FULLNAME_REQUIRED");
    }

    @Test
    void deletePurgesDataThenUser() {
        User u = seed();
        sessions.save(new Session(UuidCreator.getTimeOrderedEpoch(), u.id(), "x", "#h", null, T0, T0, T0));
        service.delete(u.id());
        assertThat(cleanup.purged).isEqualTo(u.id());
        assertThat(users.byId(u.id())).isEmpty();
        assertThat(sessions.byUser(u.id())).isEmpty();
    }

    @Test
    void revokeSessionChecksOwnership() {
        User u = seed();
        Session s = new Session(UuidCreator.getTimeOrderedEpoch(), u.id(), "x", "#h", null, T0, T0, T0);
        sessions.save(s);
        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), s.id()))
                .isInstanceOf(NotFound.class);
        service.revoke(u.id(), s.id());
        assertThat(sessions.byUser(u.id())).isEmpty();
    }
}
