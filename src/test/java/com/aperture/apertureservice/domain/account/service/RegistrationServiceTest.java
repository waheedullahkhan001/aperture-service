package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.spi.stubs.CapturingEmailSender;
import com.aperture.apertureservice.domain.account.spi.stubs.FakePasswordHasher;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedOtpGenerator;
import com.aperture.apertureservice.domain.account.spi.stubs.FixedRandomTokens;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryVerificationCodes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryVerificationCodes codes = new InMemoryVerificationCodes();
    private final CapturingEmailSender emails = new CapturingEmailSender();
    private final FixedRandomTokens tokens = new FixedRandomTokens();
    private final MutableClock clock = new MutableClock(T0);

    private final RegistrationService service = new RegistrationService(
            users, codes, new FakePasswordHasher(), new FixedOtpGenerator("123456"),
            emails, tokens, clock);

    // small helper: a Clock whose instant can be advanced in tests
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void registerCreatesUnverifiedUserAndEmailsCode() {
        service.register("user@example.com", "Test User", "abcdef1!");

        User u = users.byEmail(new Email("user@example.com")).orElseThrow();
        assertThat(u.verified()).isFalse();
        assertThat(u.passwordHash().value()).isEqualTo("h(abcdef1!)");
        VerificationCode code = codes.find(u.id(), VerificationCode.Purpose.EMAIL_VERIFICATION).orElseThrow();
        assertThat(code.codeHash()).isEqualTo("#123456");
        assertThat(code.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(emails.to("user@example.com")).hasSize(1);
        assertThat(emails.all().get(0).body()).contains("123456");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        service.register("user@example.com", "A", "abcdef1!");
        assertThatThrownBy(() -> service.register("user@example.com", "B", "abcdef1!"))
                .isInstanceOf(Conflict.class).hasFieldOrPropertyWithValue("code", "EMAIL_TAKEN");
    }

    @Test
    void registerRejectsWeakPassword() {
        assertThatThrownBy(() -> service.register("a@b.com", "A", "short"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "PASSWORD_POLICY");
    }

    @Test
    void verifyMarksUserVerifiedAndDeletesCode() {
        service.register("user@example.com", "A", "abcdef1!");
        service.verify("user@example.com", "123456");

        User u = users.byEmail(new Email("user@example.com")).orElseThrow();
        assertThat(u.verified()).isTrue();
        assertThat(codes.find(u.id(), VerificationCode.Purpose.EMAIL_VERIFICATION)).isEmpty();
    }

    @Test
    void verifyWrongCodeIncrementsAttemptsThenLocksAtFive() {
        service.register("user@example.com", "A", "abcdef1!");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify("user@example.com", "000000"))
                    .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CODE_INVALID");
        }
        assertThatThrownBy(() -> service.verify("user@example.com", "123456"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "TOO_MANY_ATTEMPTS");
    }

    @Test
    void verifyExpiredCodeRejected() {
        service.register("user@example.com", "A", "abcdef1!");
        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> service.verify("user@example.com", "123456"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CODE_EXPIRED");
    }

    @Test
    void resendHonorsCooldownAndIssuesFreshCode() {
        service.register("user@example.com", "A", "abcdef1!");
        assertThatThrownBy(() -> service.resend("user@example.com"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "RESEND_COOLDOWN");
        clock.advance(Duration.ofSeconds(61));
        service.resend("user@example.com");
        assertThat(emails.to("user@example.com")).hasSize(2);
    }

    @Test
    void resendForUnknownEmailIsSilent() {
        service.resend("ghost@example.com"); // no exception, no email — prevents enumeration
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void verifyForUnknownEmailRejectsGenerically() {
        assertThatThrownBy(() -> service.verify("ghost@example.com", "123456"))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CODE_INVALID");
    }
}
