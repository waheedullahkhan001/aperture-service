package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaRecordings implements Recordings {

    private final RecordingJpaRepository repo;

    JpaRecordings(RecordingJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean insertIfAbsent(Recording r) {
        return repo.insertIfAbsent(RecordingJpaEntity.from(r));
    }

    @Override
    public Optional<Recording> byId(UUID id) {
        return repo.findById(id).map(RecordingJpaEntity::toDomain);
    }

    @Override
    public Optional<Recording> byIdForUpdate(UUID id) {
        return repo.findForUpdate(id).map(RecordingJpaEntity::toDomain);
    }

    @Override
    public void save(Recording r) {
        repo.save(RecordingJpaEntity.from(r));
    }

    @Override
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Override
    public PageOf<Recording> byUser(UUID userId, Optional<RecordingStatus> status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RecordingJpaEntity> result = status
                .map(s -> repo.findByUserIdAndStatusOrderByStartedAtDesc(userId, s.name(), pageable))
                .orElseGet(() -> repo.findByUserIdOrderByStartedAtDesc(userId, pageable));
        return new PageOf<>(result.getContent().stream().map(RecordingJpaEntity::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    public List<UUID> idsByUser(UUID userId) {
        return repo.idsByUser(userId);
    }

    @Override
    public List<Recording> dispatchDue(Instant now) {
        return repo.dispatchDue(now).stream().map(RecordingJpaEntity::toDomain).toList();
    }

    @Override
    public List<Recording> stalePending(Instant before) {
        return repo.stalePending(before).stream().map(RecordingJpaEntity::toDomain).toList();
    }

    @Override
    public List<Recording> staleStreaming(Instant before) {
        return repo.staleStreaming(before).stream().map(RecordingJpaEntity::toDomain).toList();
    }
}
