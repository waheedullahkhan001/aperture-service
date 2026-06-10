package com.aperture.apertureservice.domain.emergency.spi;

import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertDispatchAttempts {
    AlertDispatchAttempt record(AlertDispatchAttempt attempt);
    List<AlertDispatchAttempt> failedSince(Instant since);
    int countFor(UUID recordingId, Long contactId);
    boolean anySuccess(UUID recordingId, Long contactId);
}
