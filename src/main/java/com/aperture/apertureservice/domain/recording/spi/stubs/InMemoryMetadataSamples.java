package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Stub
public class InMemoryMetadataSamples implements MetadataSamples {
    private final List<MetadataSample> all = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override public void saveAll(List<MetadataSample> samples) {
        samples.forEach(s -> all.add(new MetadataSample(seq.incrementAndGet(), s.recordingId(),
                s.latitude(), s.longitude(), s.clientTimestamp(), s.serverReceivedAt(), s.deviceInfo(),
                s.horizontalAccuracyM(), s.speedMps(), s.bearingDeg(), s.altitudeM(), s.batteryPercent())));
    }

    @Override public Optional<MetadataSample> latest(UUID recordingId) {
        return all.stream().filter(s -> s.recordingId().equals(recordingId))
                .max(Comparator.comparing(MetadataSample::clientTimestamp));
    }

    @Override public List<MetadataSample> recent(UUID recordingId, int limit) {
        return all.stream().filter(s -> s.recordingId().equals(recordingId))
                .sorted(Comparator.comparing(MetadataSample::clientTimestamp).reversed())
                .limit(limit).toList();
    }

    @Override public void deleteFor(UUID recordingId) {
        all.removeIf(s -> s.recordingId().equals(recordingId));
    }
}
