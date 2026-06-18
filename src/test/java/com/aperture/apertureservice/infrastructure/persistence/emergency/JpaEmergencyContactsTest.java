package com.aperture.apertureservice.infrastructure.persistence.emergency;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.infrastructure.persistence.emergency.jpa.JpaEmergencyContacts;
import com.aperture.apertureservice.support.JpaSliceTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JpaSliceTest
@Import(JpaEmergencyContacts.class)
class JpaEmergencyContactsTest {

    @Autowired
    JpaEmergencyContacts contacts;

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
    void saveAssignsIdAndUniquenessHelpersWork() {
        UUID userId = seedUser();
        EmergencyContact saved = contacts.save(
                new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        assertThat(saved.id()).isNotNull();
        assertThat(contacts.countByUser(userId)).isEqualTo(1);
        assertThat(contacts.existsByUserAndEmail(userId, new Email("mom@example.com"))).isTrue();
        assertThat(contacts.existsByUserAndEmail(userId, new Email("dad@example.com"))).isFalse();
        assertThat(contacts.byUser(userId)).hasSize(1);
        assertThat(contacts.byId(saved.id())).contains(saved);

        contacts.delete(saved.id());
        assertThat(contacts.byUser(userId)).isEmpty();
    }

    @Test
    void duplicateUserEmailViolatesDbConstraint() {
        UUID userId = seedUser();
        contacts.save(new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        assertThatThrownBy(() -> {
            contacts.save(new EmergencyContact(null, userId, "Mum", new Email("mom@example.com"), null));
            em.flush();
        }).isInstanceOfAny(org.springframework.dao.DataIntegrityViolationException.class,
                jakarta.persistence.PersistenceException.class);
    }

    @Test
    void byUserReturnsContactsOrderedById() {
        UUID userId = seedUser();
        EmergencyContact a = contacts.save(new EmergencyContact(null, userId, "A", new Email("a@example.com"), null));
        EmergencyContact b = contacts.save(new EmergencyContact(null, userId, "B", new Email("b@example.com"), "hi"));
        assertThat(contacts.byUser(userId)).containsExactly(a, b);
    }
}
