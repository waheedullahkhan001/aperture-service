package com.aperture.apertureservice.infrastructure.persistence.account;

import com.aperture.apertureservice.TestcontainersConfiguration;
import com.aperture.apertureservice.domain.account.Device;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.JpaDevices;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaDevices.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaDevicesTest {

    @Autowired
    JpaDevices devices;

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
    void roundTripIncludingNullableRevokedAt() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        Device d = new Device(UuidCreator.getTimeOrderedEpoch(), userId, "Pixel 8", "#tok", t, t, null);
        devices.save(d);

        assertThat(devices.byTokenHash("#tok")).contains(d);
        devices.save(d.revoke(t.plusSeconds(5)));
        assertThat(devices.byId(d.id()).orElseThrow().revoked()).isTrue();
        assertThat(devices.byUser(userId)).hasSize(1);
    }
}
