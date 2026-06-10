package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import com.aperture.apertureservice.domain.account.spi.AccountCleanup;
import com.aperture.apertureservice.domain.account.spi.Devices;
import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.OtpGenerator;
import com.aperture.apertureservice.domain.account.spi.PasswordHasher;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.account.spi.VerificationCodes;
import com.aperture.apertureservice.domain.account.spi.stubs.NoopAccountCleanup;
import com.aperture.apertureservice.domain.account.service.AuthenticationService;
import com.aperture.apertureservice.domain.account.service.DeviceService;
import com.aperture.apertureservice.domain.account.service.PasswordResetService;
import com.aperture.apertureservice.domain.account.service.ProfileService;
import com.aperture.apertureservice.domain.account.service.RegistrationService;
import com.aperture.apertureservice.infrastructure.email.LoggingEmailSender;
import com.aperture.apertureservice.infrastructure.security.BcryptPasswordHasher;
import com.aperture.apertureservice.infrastructure.security.JwtTokenIssuer;
import com.aperture.apertureservice.infrastructure.security.SecureOtpGenerator;
import com.aperture.apertureservice.infrastructure.security.SecureRandomTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AccountConfiguration {

    @Bean
    Clock clock() {
        // micro-tick: Postgres timestamptz stores microseconds; emitting micro-precision
        // Instants keeps record-equality stable across DB round-trips
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }

    @Bean
    RandomTokens randomTokens() {
        return new SecureRandomTokens();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BcryptPasswordHasher();
    }

    @Bean
    OtpGenerator otpGenerator() {
        return new SecureOtpGenerator();
    }

    @Bean
    TokenIssuer tokenIssuer(AppProperties props, Clock clock) {
        return new JwtTokenIssuer(props.jwt().secret(), clock);
    }

    @Bean
    @Profile("!prod")
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }

    // Temporary shim until JpaAccountCleanup lands (Task 21)
    @Bean
    AccountCleanup accountCleanup() {
        return new NoopAccountCleanup();
    }

    @Bean
    RegistrationService registrationService(Users users, VerificationCodes codes, PasswordHasher hasher,
                                            OtpGenerator otp, EmailSender emails, RandomTokens tokens, Clock clock) {
        return new RegistrationService(users, codes, hasher, otp, emails, tokens, clock);
    }

    @Bean
    AuthenticationService authenticationService(Users users, Sessions sessions, PasswordHasher hasher,
                                                TokenIssuer tokenIssuer, RandomTokens tokens, Clock clock,
                                                AppProperties props) {
        return new AuthenticationService(users, sessions, hasher, tokenIssuer, tokens, clock,
                props.jwt().accessTtl(), props.session().refreshTtl());
    }

    @Bean
    PasswordResetService passwordResetService(Users users, Sessions sessions, VerificationCodes codes,
                                              PasswordHasher hasher, OtpGenerator otp, EmailSender emails,
                                              RandomTokens tokens, Clock clock) {
        return new PasswordResetService(users, sessions, codes, hasher, otp, emails, tokens, clock);
    }

    @Bean
    ProfileService profileService(Users users, Sessions sessions, AccountCleanup cleanup) {
        return new ProfileService(users, sessions, cleanup);
    }

    @Bean
    DeviceService deviceService(Users users, Devices devices, RandomTokens tokens, Clock clock) {
        return new DeviceService(users, devices, tokens, clock);
    }
}
