package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.AuthTokens;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.stubs.FakePasswordHasher;
import com.aperture.apertureservice.domain.account.spi.stubs.FakeTokenIssuer;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemorySessions;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.github.f4b6a3.uuid.UuidCreator;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemorySessions sessions = new InMemorySessions();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private final AuthenticationService service = new AuthenticationService(
            users, sessions, hasher, new FakeTokenIssuer(), tokens, clock, ACCESS_TTL, REFRESH_TTL);

    private User verifiedUser() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "U",
                new HashedPassword(hasher.hash("abcdef1!")), true, T0);
        users.save(u);
        return u;
    }

    @Test
    void logInReturnsTokensAndPersistsSession() {
        User u = verifiedUser();
        AuthTokens t = service.logIn("u@example.com", "abcdef1!", "Firefox on Fedora");

        assertThat(t.refreshToken()).startsWith("aprt_");
        assertThat(t.expiresInSeconds()).isEqualTo(900);
        Session s = sessions.byUser(u.id()).get(0);
        assertThat(s.refreshTokenHash()).isEqualTo(tokens.hash(t.refreshToken()));
        assertThat(s.label()).isEqualTo("Firefox on Fedora");
        assertThat(s.expiresAt()).isEqualTo(T0.plus(REFRESH_TTL));
        assertThat(t.accessToken()).isEqualTo("jwt." + u.id() + "." + s.id());
    }

    @Test
    void logInRejectsWrongPasswordAndUnknownEmailIdentically() {
        verifiedUser();
        for (var call : List.<ThrowableAssert.ThrowingCallable>of(
                () -> service.logIn("u@example.com", "wrong!!1", "x"),
                () -> service.logIn("ghost@example.com", "abcdef1!", "x"))) {
            assertThatThrownBy(call).isInstanceOf(Unauthorized.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        }
    }

    @Test
    void logInRejectsUnverifiedUser() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "U",
                new HashedPassword(hasher.hash("abcdef1!")), false, T0);
        users.save(u);
        assertThatThrownBy(() -> service.logIn("u@example.com", "abcdef1!", "x"))
                .isInstanceOf(Forbidden.class).hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");
    }

    @Test
    void refreshRotatesAndImmediateRetryGetsNewTokenInsteadOfNuke() {
        User u = verifiedUser();
        AuthTokens first = service.logIn("u@example.com", "abcdef1!", "x");
        AuthTokens second = service.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // immediate retry with the rotated-away token: response-lost scenario -> fresh rotation, sessions intact
        AuthTokens third = service.refresh(first.refreshToken());
        assertThat(third.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(sessions.byUser(u.id())).hasSize(1);
    }

    @Test
    void staleReuseOutsideGraceRevokesAllSessions() {
        User u = verifiedUser();
        AuthTokens first = service.logIn("u@example.com", "abcdef1!", "x");
        AuthTokens second = service.refresh(first.refreshToken());

        AuthenticationService later = new AuthenticationService(users, sessions, hasher,
                new FakeTokenIssuer(), tokens, Clock.fixed(T0.plus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                ACCESS_TTL, REFRESH_TTL);
        assertThatThrownBy(() -> later.refresh(first.refreshToken()))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "REFRESH_REUSED");
        assertThat(sessions.byUser(u.id())).isEmpty();
        // the rotated token is also dead now that the nuke cleared everything
        assertThatThrownBy(() -> later.refresh(second.refreshToken()))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshRejectsGarbageToken() {
        assertThatThrownBy(() -> service.refresh("aprt_garbage"))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshRejectsExpiredSession() {
        verifiedUser();
        AuthTokens t = service.logIn("u@example.com", "abcdef1!", "x");
        AuthenticationService expiredView = new AuthenticationService(users, sessions, hasher,
                new FakeTokenIssuer(), tokens, Clock.fixed(T0.plus(Duration.ofDays(31)), ZoneOffset.UTC),
                ACCESS_TTL, REFRESH_TTL);
        assertThatThrownBy(() -> expiredView.refresh(t.refreshToken()))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshSlidesExpiryForward() {
        User u = verifiedUser();
        AuthTokens t = service.logIn("u@example.com", "abcdef1!", "x");
        Instant later = T0.plus(Duration.ofDays(1));
        AuthenticationService dayLater = new AuthenticationService(users, sessions, hasher,
                new FakeTokenIssuer(), tokens, Clock.fixed(later, ZoneOffset.UTC), ACCESS_TTL, REFRESH_TTL);
        dayLater.refresh(t.refreshToken());
        assertThat(sessions.byUser(u.id()).get(0).expiresAt()).isEqualTo(later.plus(REFRESH_TTL));
    }

    @Test
    void staleReuseOnExpiredSessionCleansUpWithoutNuking() {
        User u = verifiedUser();
        AuthTokens first = service.logIn("u@example.com", "abcdef1!", "x");
        service.refresh(first.refreshToken());
        AuthenticationService muchLater = new AuthenticationService(users, sessions, hasher,
                new FakeTokenIssuer(), tokens, Clock.fixed(T0.plus(Duration.ofDays(31)), ZoneOffset.UTC),
                ACCESS_TTL, REFRESH_TTL);
        assertThatThrownBy(() -> muchLater.refresh(first.refreshToken()))
                .isInstanceOf(Unauthorized.class).hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
        assertThat(sessions.byUser(u.id())).isEmpty(); // zombie deleted, but not via the nuke path
    }

    @Test
    void logOutDeletesSession() {
        User u = verifiedUser();
        service.logIn("u@example.com", "abcdef1!", "x");
        UUID sessionId = sessions.byUser(u.id()).get(0).id();
        service.logOut(sessionId);
        assertThat(sessions.byUser(u.id())).isEmpty();
    }
}
