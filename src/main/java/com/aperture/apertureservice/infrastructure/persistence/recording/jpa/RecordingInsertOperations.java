package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

interface RecordingInsertOperations {
    boolean insertIfAbsent(RecordingJpaEntity entity);
}
