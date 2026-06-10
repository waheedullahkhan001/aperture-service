package com.aperture.apertureservice.domain.emergency.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Stub
public class InMemoryEmergencyContacts implements EmergencyContacts {
    private final Map<Long, EmergencyContact> byId = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public List<EmergencyContact> byUser(UUID userId) {
        return byId.values().stream().filter(c -> c.userId().equals(userId))
                .sorted(Comparator.comparing(EmergencyContact::id)).toList();
    }

    @Override
    public Optional<EmergencyContact> byId(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public int countByUser(UUID userId) {
        return byUser(userId).size();
    }

    @Override
    public boolean existsByUserAndEmail(UUID userId, Email email) {
        return byUser(userId).stream().anyMatch(c -> c.email().equals(email));
    }

    @Override
    public EmergencyContact save(EmergencyContact c) {
        Long id = c.id() != null ? c.id() : seq.incrementAndGet();
        EmergencyContact saved = new EmergencyContact(id, c.userId(), c.name(), c.email(), c.messageOverride());
        byId.put(id, saved);
        return saved;
    }

    @Override
    public void delete(Long id) {
        byId.remove(id);
    }
}
