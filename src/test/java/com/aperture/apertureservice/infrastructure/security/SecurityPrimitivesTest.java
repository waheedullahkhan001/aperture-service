package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.AccessClaims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrimitivesTest {

    @Test
    void bcryptRoundTrip() {
        BcryptPasswordHasher hasher = new BcryptPasswordHasher();
        String hash = hasher.hash("abcdef1!");
        assertThat(hash).startsWith("$2");
        assertThat(hasher.matches("abcdef1!", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void secureTokensArePrefixedUniqueAndHashStable() {
        SecureRandomTokens tokens = new SecureRandomTokens();
        String a = tokens.token("apd_");
        String b = tokens.token("apd_");
        assertThat(a).startsWith("apd_").hasSizeGreaterThan(40);
        assertThat(a).isNotEqualTo(b);
        assertThat(tokens.hash(a)).isEqualTo(tokens.hash(a)).hasSize(64); // sha-256 hex
        assertThat(tokens.hash(a)).isNotEqualTo(tokens.hash(b));
    }

    @Test
    void otpIsSixDigits() {
        SecureOtpGenerator otp = new SecureOtpGenerator();
        for (int i = 0; i < 50; i++) {
            assertThat(otp.sixDigits()).matches("\\d{6}");
        }
    }

    @Test
    void jwtRoundTripAndExpiryAndTamper() {
        String secret = Base64.getEncoder().encodeToString("d".repeat(64).getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.parse("2026-06-07T12:00:00Z");
        JwtTokenIssuer issuer = new JwtTokenIssuer(secret, Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String token = issuer.issue(userId, sessionId, Duration.ofMinutes(15));
        AccessClaims claims = issuer.validate(token).orElseThrow();
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo(sessionId);

        JwtTokenIssuer afterExpiry = new JwtTokenIssuer(secret,
                Clock.fixed(now.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThat(afterExpiry.validate(token)).isEmpty();

        assertThat(issuer.validate(token + "x")).isEmpty();
        assertThat(issuer.validate("garbage")).isEmpty();
    }
}
