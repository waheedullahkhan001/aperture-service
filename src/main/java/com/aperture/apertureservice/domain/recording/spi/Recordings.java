package com.aperture.apertureservice.domain.recording.spi;

import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Recordings {
    /** True if this call inserted the row; false if it already existed.
     *  Implementations must be race-safe on the primary key. */
    boolean insertIfAbsent(Recording recording);
    Optional<Recording> byId(UUID id);
    /** Same as byId but takes a row lock; used by alert dispatch. */
    Optional<Recording> byIdForUpdate(UUID id);
    void save(Recording recording);
    void delete(UUID id);
    PageOf<Recording> byUser(UUID userId, Optional<RecordingStatus> status, int page, int size);
    List<UUID> idsByUser(UUID userId);
    List<Recording> dispatchDue(Instant now);
    List<Recording> stalePending(Instant before);
    /** RECORDING rows with no recent segment activity; adapter joins segments, stub approximates. */
    List<Recording> staleStreaming(Instant before);
}
