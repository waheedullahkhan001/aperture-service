package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.PasswordPolicy;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.api.RegisterAccount;
import com.aperture.apertureservice.domain.account.api.ResendVerification;
import com.aperture.apertureservice.domain.account.api.VerifyEmail;
import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.OtpGenerator;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.account.spi.VerificationCodes;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@DomainService
public class RegistrationService implements RegisterAccount, VerifyEmail, ResendVerification {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private final Users users;
    private final VerificationCodes codes;
    private final PasswordHasher hasher;
    private final OtpGenerator otp;
    private final EmailSender emails;
    private final RandomTokens tokens;
    private final Clock clock;

    public RegistrationService(Users users, VerificationCodes codes, PasswordHasher hasher,
                               OtpGenerator otp, EmailSender emails, RandomTokens tokens, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.hasher = hasher;
        this.otp = otp;
        this.emails = emails;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void register(String rawEmail, String fullname, String rawPassword) {
        Email email = new Email(rawEmail);
        PasswordPolicy.check(rawPassword);
        if (fullname == null || fullname.isBlank()) {
            throw new BadRequest("FULLNAME_REQUIRED", "Full name is required");
        }
        if (users.byEmail(email).isPresent()) {
            throw new Conflict("EMAIL_TAKEN", "This email is already registered");
        }
        Instant now = clock.instant();
        User user = new User(UuidCreator.getTimeOrderedEpoch(), email, fullname.trim(),
                new HashedPassword(hasher.hash(rawPassword)), false, now);
        users.save(user);
        issueCode(user, VerificationCode.Purpose.EMAIL_VERIFICATION,
                "Your Aperture verification code",
                "Your verification code is %s. It expires in 10 minutes.");
    }

    @Override
    @Transactional
    public void verify(String rawEmail, String presented) {
        User user = users.byEmail(new Email(rawEmail))
                .orElseThrow(() -> new BadRequest("CODE_INVALID", "Invalid code"));
        VerificationCode code = codes.find(user.id(), VerificationCode.Purpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BadRequest("CODE_INVALID", "Invalid code"));
        checkCode(code, presented);
        users.save(user.verifiedNow());
        codes.delete(user.id(), VerificationCode.Purpose.EMAIL_VERIFICATION);
    }

    @Override
    @Transactional
    public void resend(String rawEmail) {
        Optional<User> user = users.byEmail(new Email(rawEmail));
        if (user.isEmpty() || user.get().verified()) {
            return; // silent — no account enumeration
        }
        Instant now = clock.instant();
        codes.find(user.get().id(), VerificationCode.Purpose.EMAIL_VERIFICATION)
                .filter(c -> c.lastSentAt().plus(RESEND_COOLDOWN).isAfter(now))
                .ifPresent(c -> { throw new BadRequest("RESEND_COOLDOWN", "Wait before requesting another code"); });
        issueCode(user.get(), VerificationCode.Purpose.EMAIL_VERIFICATION,
                "Your Aperture verification code",
                "Your verification code is %s. It expires in 10 minutes.");
    }

    /** Code rules: 5 attempts max, 10-minute TTL, attempt counter bumps on mismatch.
     *  PasswordResetService applies the same rules with its own inline copy (kept separate
     *  to avoid coupling the two services; the rules are locked by both test classes). */
    private void checkCode(VerificationCode code, String presented) {
        Instant now = clock.instant();
        if (code.attempts() >= MAX_ATTEMPTS) {
            throw new BadRequest("TOO_MANY_ATTEMPTS", "Too many attempts; request a new code");
        }
        if (code.expiresAt().isBefore(now)) {
            throw new BadRequest("CODE_EXPIRED", "Code expired; request a new code");
        }
        if (!tokens.hash(presented).equals(code.codeHash())) {
            codes.save(code.withAttempt());
            throw new BadRequest("CODE_INVALID", "Invalid code");
        }
    }

    private void issueCode(User user, VerificationCode.Purpose purpose, String subject, String bodyTemplate) {
        Instant now = clock.instant();
        String code = otp.sixDigits();
        codes.save(new VerificationCode(user.id(), purpose, tokens.hash(code),
                now.plus(CODE_TTL), 0, now));
        emails.send(user.email().value(), subject, bodyTemplate.formatted(code));
    }
}
