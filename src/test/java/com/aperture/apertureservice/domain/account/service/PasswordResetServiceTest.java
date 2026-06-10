package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.spi.stubs.CapturingEmailSender;
import com.aperture.apertureservice.domain.account.spi.stubs.FakePasswordHasher;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedOtpGenerator;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemorySessions;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryVerificationCodes;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemorySessions sessions = new InMemorySessions();
    private final InMemoryVerificationCodes codes = new InMemoryVerificationCodes();
    private final CapturingEmailSender emails = new CapturingEmailSender();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private final PasswordResetService service = new PasswordResetService(
            users, sessions, codes, hasher, new FixedOtpGenerator("654321"), emails, tokens, clock);

    private User user(boolean verified) {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("u@example.com"), "U",
                new HashedPassword(hasher.hash("oldpass1!")), verified, T0);
        users.save(u);
        return u;
    }

    @Test
    void requestSendsCodeForKnownUser() {
        User u = user(true);
        service.request("u@example.com");
        assertThat(codes.find(u.id(), VerificationCode.Purpose.PASSWORD_RESET)).isPresent();
        assertThat(emails.to("u@example.com")).hasSize(1);
    }

    @Test
    void requestIsSilentForUnknownEmail() {
        service.request("ghost@example.com");
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void resetUpdatesPasswordKillsSessionsAndDeletesCode() {
        User u = user(true);
        sessions.save(new Session(UuidCreator.getTimeOrderedEpoch(), u.id(), "x", "#h", null, T0, T0,
                T0.plus(Duration.ofDays(30))));
        service.request("u@example.com");
        service.reset("u@example.com", "654321", "newpass1!");

        assertThat(users.byId(u.id()).orElseThrow().passwordHash().value()).isEqualTo("h(newpass1!)");
        assertThat(sessions.byUser(u.id())).isEmpty();
        assertThat(codes.find(u.id(), VerificationCode.Purpose.PASSWORD_RESET)).isEmpty();
    }

    @Test
    void resetEnforcesPasswordPolicy() {
        user(true);
        service.request("u@example.com");
        assertThatThrownBy(() -> service.reset("u@example.com", "654321", "weak"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "PASSWORD_POLICY");
    }

    @Test
    void resetWithWrongCodeRejected() {
        user(true);
        service.request("u@example.com");
        assertThatThrownBy(() -> service.reset("u@example.com", "000000", "newpass1!"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CODE_INVALID");
    }

    @Test
    void requestHonorsCooldown() {
        user(true);
        service.request("u@example.com");
        assertThatThrownBy(() -> service.request("u@example.com"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "RESEND_COOLDOWN");
    }

    @Test
    void resetLocksAfterFiveWrongAttempts() {
        user(true);
        service.request("u@example.com");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.reset("u@example.com", "000000", "newpass1!"))
                    .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CODE_INVALID");
        }
        assertThatThrownBy(() -> service.reset("u@example.com", "654321", "newpass1!"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "TOO_MANY_ATTEMPTS");
    }
}
