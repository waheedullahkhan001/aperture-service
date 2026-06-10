package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.Conflict;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.AddEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.emergency.api.RemoveEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.UpdateEmergencyContact;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@DomainService
public class ContactsService implements AddEmergencyContact, UpdateEmergencyContact,
        RemoveEmergencyContact, ListEmergencyContacts {

    static final int MAX_CONTACTS = 10;

    private final EmergencyContacts contacts;

    public ContactsService(EmergencyContacts contacts) {
        this.contacts = contacts;
    }

    @Override
    @Transactional
    public EmergencyContact add(UUID userId, String name, String rawEmail, String messageOverride) {
        Email email = new Email(rawEmail);
        requireName(name);
        if (contacts.countByUser(userId) >= MAX_CONTACTS) {
            throw new BadRequest("CONTACT_LIMIT", "At most " + MAX_CONTACTS + " contacts allowed");
        }
        if (contacts.existsByUserAndEmail(userId, email)) {
            throw new Conflict("CONTACT_EXISTS", "A contact with this email already exists");
        }
        return contacts.save(new EmergencyContact(null, userId, name.trim(), email, blankToNull(messageOverride)));
    }

    @Override
    @Transactional
    public EmergencyContact update(UUID userId, Long contactId, String name, String rawEmail, String messageOverride) {
        EmergencyContact existing = owned(userId, contactId);
        Email email = new Email(rawEmail);
        requireName(name);
        if (!existing.email().equals(email) && contacts.existsByUserAndEmail(userId, email)) {
            throw new Conflict("CONTACT_EXISTS", "A contact with this email already exists");
        }
        return contacts.save(new EmergencyContact(existing.id(), userId, name.trim(), email,
                blankToNull(messageOverride)));
    }

    @Override
    @Transactional
    public void remove(UUID userId, Long contactId) {
        contacts.delete(owned(userId, contactId).id());
    }

    @Override
    public List<EmergencyContact> list(UUID userId) {
        return contacts.byUser(userId);
    }

    private EmergencyContact owned(UUID userId, Long contactId) {
        return contacts.byId(contactId).filter(c -> c.userId().equals(userId))
                .orElseThrow(() -> new NotFound("CONTACT_NOT_FOUND", "Contact not found"));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequest("CONTACT_NAME_REQUIRED", "Contact name is required");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
