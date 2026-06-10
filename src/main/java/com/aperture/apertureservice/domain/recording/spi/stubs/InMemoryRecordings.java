package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.Recordings;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemoryRecordings implements Recordings {
    private final Map<UUID, Recording> byId = new ConcurrentHashMap<>();

    @Override public boolean insertIfAbsent(Recording r) { return byId.putIfAbsent(r.id(), r) == null; }
    @Override public Optional<Recording> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
    @Override public Optional<Recording> byIdForUpdate(UUID id) { return byId(id); }
    @Override public void save(Recording r) { byId.put(r.id(), r); }
    @Override public void delete(UUID id) { byId.remove(id); }

    @Override public PageOf<Recording> byUser(UUID userId, Optional<RecordingStatus> status, int page, int size) {
        List<Recording> all = byId.values().stream()
                .filter(r -> r.userId().equals(userId))
                .filter(r -> status.isEmpty() || r.status() == status.get())
                .sorted(Comparator.comparing(Recording::startedAt).reversed())
                .toList();
        List<Recording> slice = all.stream().skip((long) page * size).limit(size).toList();
        return new PageOf<>(slice, page, size, all.size());
    }

    @Override public List<UUID> idsByUser(UUID userId) {
        return byId.values().stream().filter(r -> r.userId().equals(userId)).map(Recording::id).toList();
    }

    @Override public List<Recording> dispatchDue(Instant now) {
        return byId.values().stream()
                .filter(Recording::live)
                .filter(r -> r.countdownEndsAt() != null && !r.countdownEndsAt().isAfter(now))
                .filter(r -> r.alertsDispatchedAt() == null)
                .toList();
    }

    @Override public List<Recording> stalePending(Instant before) {
        return byId.values().stream()
                .filter(r -> r.status() == RecordingStatus.PENDING && r.startedAt().isBefore(before))
                .toList();
    }

    // Stub approximation: no segment knowledge here; the JPA adapter joins segments (Task 20).
    @Override public List<Recording> staleStreaming(Instant before) {
        return byId.values().stream()
                .filter(r -> r.status() == RecordingStatus.RECORDING && r.startedAt().isBefore(before))
                .toList();
    }
}
