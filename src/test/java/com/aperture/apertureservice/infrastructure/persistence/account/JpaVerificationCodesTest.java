package com.aperture.apertureservice.infrastructure.persistence.account;

import com.aperture.apertureservice.TestcontainersConfiguration;
import com.aperture.apertureservice.domain.account.VerificationCode;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.JpaVerificationCodes;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaVerificationCodes.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaVerificationCodesTest {

    @Autowired
    JpaVerificationCodes codes;

    @Autowired
    TestEntityManager em;

    private UUID seedUser() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        em.getEntityManager().createNativeQuery(
                "insert into users (id, email, fullname, password_hash, verified, created_at) " +
                "values (?1, ?2, 'U', 'h', false, now())")
                .setParameter(1, id)
                .setParameter(2, "u-" + id + "@example.com")
                .executeUpdate();
        return id;
    }

    @Test
    void upsertsPerUserAndPurpose() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        codes.save(new VerificationCode(userId, VerificationCode.Purpose.EMAIL_VERIFICATION, "#1",
                t.plusSeconds(600), 0, t));
        codes.save(new VerificationCode(userId, VerificationCode.Purpose.EMAIL_VERIFICATION, "#2",
                t.plusSeconds(600), 1, t));

        VerificationCode found = codes.find(userId, VerificationCode.Purpose.EMAIL_VERIFICATION).orElseThrow();
        assertThat(found.codeHash()).isEqualTo("#2");
        assertThat(found.attempts()).isEqualTo(1);
        codes.delete(userId, VerificationCode.Purpose.EMAIL_VERIFICATION);
        assertThat(codes.find(userId, VerificationCode.Purpose.EMAIL_VERIFICATION)).isEmpty();
    }
}
