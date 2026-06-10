package com.aperture.apertureservice.infrastructure.persistence.emergency.jpa;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "emergency_contacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "email"}))
class ContactJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String email;

    @Column(name = "message_override", length = 2000)
    String messageOverride;

    protected ContactJpaEntity() {}

    static ContactJpaEntity from(EmergencyContact c) {
        ContactJpaEntity e = new ContactJpaEntity();
        e.id = c.id();
        e.userId = c.userId();
        e.name = c.name();
        e.email = c.email().value();
        e.messageOverride = c.messageOverride();
        return e;
    }

    EmergencyContact toDomain() {
        return new EmergencyContact(id, userId, name, new Email(email), messageOverride);
    }
}
