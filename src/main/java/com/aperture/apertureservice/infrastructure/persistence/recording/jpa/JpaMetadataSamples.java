package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaMetadataSamples implements MetadataSamples {

    private final MetadataSampleJpaRepository repo;

    JpaMetadataSamples(MetadataSampleJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void saveAll(List<MetadataSample> samples) {
        repo.saveAll(samples.stream().map(MetadataSampleJpaEntity::from).toList());
    }

    @Override
    public Optional<MetadataSample> latest(UUID recordingId) {
        return repo.findFirstByRecordingIdOrderByClientTimestampDesc(recordingId)
                .map(MetadataSampleJpaEntity::toDomain);
    }

    @Override
    public List<MetadataSample> recent(UUID recordingId, int limit) {
        return repo.findByRecordingIdOrderByClientTimestampDesc(recordingId, PageRequest.of(0, limit))
                .stream().map(MetadataSampleJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteFor(UUID recordingId) {
        repo.deleteByRecordingId(recordingId);
    }
}
