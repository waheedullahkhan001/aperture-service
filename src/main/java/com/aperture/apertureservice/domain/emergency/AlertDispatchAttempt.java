package com.aperture.apertureservice.domain.emergency;

import java.time.Instant;
import java.util.UUID;

public record AlertDispatchAttempt(Long id, UUID recordingId, Long contactId,
                                   Instant attemptedAt, boolean success, String errorMessage) {}
