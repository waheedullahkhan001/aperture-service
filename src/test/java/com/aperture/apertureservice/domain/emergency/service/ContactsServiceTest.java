package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.spi.stubs.InMemoryEmergencyContacts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactsServiceTest {

    private final InMemoryEmergencyContacts contacts = new InMemoryEmergencyContacts();
    private final ContactsService service = new ContactsService(contacts);
    private final UUID userId = UUID.randomUUID();

    @Test
    void addNormalizesEmailAndAssignsId() {
        EmergencyContact c = service.add(userId, "Mom", "Mom@Example.com", null);
        assertThat(c.id()).isNotNull();
        assertThat(c.email().value()).isEqualTo("mom@example.com");
    }

    @Test
    void addRejectsDuplicateEmailPerUser() {
        service.add(userId, "Mom", "mom@example.com", null);
        assertThatThrownBy(() -> service.add(userId, "Mother", "MOM@example.com", null))
                .isInstanceOf(Conflict.class).hasFieldOrPropertyWithValue("code", "CONTACT_EXISTS");
    }

    @Test
    void addEnforcesMaxTen() {
        for (int i = 0; i < 10; i++) {
            service.add(userId, "C" + i, "c" + i + "@example.com", null);
        }
        assertThatThrownBy(() -> service.add(userId, "C11", "c11@example.com", null))
                .isInstanceOf(BadRequest.class).hasFieldOrPropertyWithValue("code", "CONTACT_LIMIT");
    }

    @Test
    void updateKeepingOwnEmailIsAllowed() {
        EmergencyContact c = service.add(userId, "Mom", "mom@example.com", null);
        EmergencyContact updated = service.update(userId, c.id(), "Mother", "mom@example.com", "Custom {{streamUrl}}");
        assertThat(updated.name()).isEqualTo("Mother");
        assertThat(updated.messageOverride()).isEqualTo("Custom {{streamUrl}}");
    }

    @Test
    void updateAndRemoveCheckOwnership() {
        EmergencyContact c = service.add(userId, "Mom", "mom@example.com", null);
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), c.id(), "X", "x@example.com", null))
                .isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> service.remove(UUID.randomUUID(), c.id()))
                .isInstanceOf(NotFound.class);
        service.remove(userId, c.id());
        assertThat(service.list(userId)).isEmpty();
    }

    @Test
    void updateToNewUniqueEmailAllowedAndDuplicateRejected() {
        EmergencyContact mom = service.add(userId, "Mom", "mom@example.com", null);
        service.add(userId, "Dad", "dad@example.com", null);

        EmergencyContact updated = service.update(userId, mom.id(), "Mom", "mum@example.com", null);
        assertThat(updated.email().value()).isEqualTo("mum@example.com");

        assertThatThrownBy(() -> service.update(userId, mom.id(), "Mom", "dad@example.com", null))
                .isInstanceOf(Conflict.class).hasFieldOrPropertyWithValue("code", "CONTACT_EXISTS");
    }

    @Test
    void blankMessageOverrideStoredAsNull() {
        EmergencyContact c = service.add(userId, "Mom", "mom@example.com", "   ");
        assertThat(c.messageOverride()).isNull();
        EmergencyContact updated = service.update(userId, c.id(), "Mom", "mom@example.com", "");
        assertThat(updated.messageOverride()).isNull();
    }
}
