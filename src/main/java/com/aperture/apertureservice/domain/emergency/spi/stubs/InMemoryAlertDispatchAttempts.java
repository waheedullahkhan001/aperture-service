package com.aperture.apertureservice.domain.emergency.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import com.aperture.apertureservice.domain.emergency.spi.AlertDispatchAttempts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Stub
public class InMemoryAlertDispatchAttempts implements AlertDispatchAttempts {
    private final List<AlertDispatchAttempt> all = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public AlertDispatchAttempt record(AlertDispatchAttempt a) {
        AlertDispatchAttempt saved = new AlertDispatchAttempt(seq.incrementAndGet(), a.recordingId(),
                a.contactId(), a.attemptedAt(), a.success(), a.errorMessage());
        all.add(saved);
        return saved;
    }

    @Override
    public List<AlertDispatchAttempt> failedSince(Instant since) {
        return all.stream().filter(a -> !a.success() && !a.attemptedAt().isBefore(since)).toList();
    }

    @Override
    public int countFor(UUID recordingId, Long contactId) {
        return (int) all.stream().filter(a -> recordingId.equals(a.recordingId())
                && contactId.equals(a.contactId())).count();
    }

    @Override
    public boolean anySuccess(UUID recordingId, Long contactId) {
        return all.stream().anyMatch(a -> a.success() && recordingId.equals(a.recordingId())
                && contactId.equals(a.contactId()));
    }

    public List<AlertDispatchAttempt> all() {
        return List.copyOf(all);
    }
}
