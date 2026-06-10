package com.aperture.apertureservice.domain.recording.spi;

import com.aperture.apertureservice.domain.recording.MetadataSample;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetadataSamples {
    void saveAll(List<MetadataSample> samples);
    Optional<MetadataSample> latest(UUID recordingId);
    List<MetadataSample> recent(UUID recordingId, int limit);
    void deleteFor(UUID recordingId);
}
