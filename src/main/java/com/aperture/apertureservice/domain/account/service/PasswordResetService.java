package com.aperture.apertureservice.domain.account.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.PasswordPolicy;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.domain.account.api.RequestPasswordReset;
import com.aperture.apertureservice.domain.account.api.ResetPassword;
import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.OtpGenerator;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.account.spi.VerificationCodes;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@DomainService
public class PasswordResetService implements RequestPasswordReset, ResetPassword {

    private final Users users;
    private final Sessions sessions;
    private final VerificationCodes codes;
    private final PasswordHasher hasher;
    private final OtpGenerator otp;
    private final EmailSender emails;
    private final RandomTokens tokens;
    private final Clock clock;

    public PasswordResetService(Users users, Sessions sessions, VerificationCodes codes,
                                PasswordHasher hasher, OtpGenerator otp, EmailSender emails,
                                RandomTokens tokens, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.codes = codes;
        this.hasher = hasher;
        this.otp = otp;
        this.emails = emails;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void request(String rawEmail) {
        Optional<User> user = users.byEmail(new Email(rawEmail));
        if (user.isEmpty()) {
            return; // silent — no account enumeration
        }
        Instant now = clock.instant();
        codes.find(user.get().id(), VerificationCode.Purpose.PASSWORD_RESET)
                .filter(c -> c.lastSentAt().plus(RegistrationService.RESEND_COOLDOWN).isAfter(now))
                .ifPresent(c -> { throw new BadRequest("RESEND_COOLDOWN", "Wait before requesting another code"); });
        String code = otp.sixDigits();
        codes.save(new VerificationCode(user.get().id(), VerificationCode.Purpose.PASSWORD_RESET,
                tokens.hash(code), now.plus(RegistrationService.CODE_TTL), 0, now));
        emails.send(user.get().email().value(), "Your Aperture password reset code",
                "Your password reset code is %s. It expires in 10 minutes.".formatted(code));
    }

    @Override
    @Transactional(dontRollbackOn = BadRequest.class)  // attempt-counter increment must survive the throw
    public void reset(String rawEmail, String presented, String newPassword) {
        User user = users.byEmail(new Email(rawEmail))
                .orElseThrow(() -> new BadRequest("CODE_INVALID", "Invalid code"));
        VerificationCode code = codes.find(user.id(), VerificationCode.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequest("CODE_INVALID", "Invalid code"));
        Instant now = clock.instant();
        if (code.attempts() >= RegistrationService.MAX_ATTEMPTS) {
            throw new BadRequest("TOO_MANY_ATTEMPTS", "Too many attempts; request a new code");
        }
        if (code.expiresAt().isBefore(now)) {
            throw new BadRequest("CODE_EXPIRED", "Code expired; request a new code");
        }
        if (!tokens.hash(presented).equals(code.codeHash())) {
            codes.save(code.withAttempt());
            throw new BadRequest("CODE_INVALID", "Invalid code");
        }
        PasswordPolicy.check(newPassword);
        users.save(user.withPasswordHash(new HashedPassword(hasher.hash(newPassword))));
        sessions.deleteAllForUser(user.id());
        codes.delete(user.id(), VerificationCode.Purpose.PASSWORD_RESET);
    }
}
