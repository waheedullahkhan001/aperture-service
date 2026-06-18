package com.aperture.apertureservice.infrastructure.persistence.emergency;

import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.infrastructure.persistence.emergency.jpa.JpaAlertConfigurations;
import com.aperture.apertureservice.support.JpaSliceTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JpaSliceTest
@Import(JpaAlertConfigurations.class)
class JpaAlertConfigurationsTest {

    @Autowired
    JpaAlertConfigurations configs;

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
    void insertThenUpdateUpsertSemantics() {
        UUID userId = seedUser();
        assertThat(configs.byUser(userId)).isEmpty();

        configs.save(new AlertConfiguration(userId, 45, "First {{streamUrl}}"));
        assertThat(configs.byUser(userId).orElseThrow().countdownDurationSeconds()).isEqualTo(45);

        configs.save(new AlertConfiguration(userId, 0, "Second {{streamUrl}}"));
        AlertConfiguration updated = configs.byUser(userId).orElseThrow();
        assertThat(updated.countdownDurationSeconds()).isZero();
        assertThat(updated.messageTemplate()).isEqualTo("Second {{streamUrl}}");
    }
}
