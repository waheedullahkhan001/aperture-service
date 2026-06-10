package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.domain.recording.MetadataSample;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metadata_samples",
        indexes = @Index(name = "idx_metadata_recording_time", columnList = "recording_id,client_timestamp"))
class MetadataSampleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "recording_id", nullable = false)
    UUID recordingId;

    @Column(precision = 9, scale = 6)
    BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    BigDecimal longitude;

    @Column(name = "client_timestamp", nullable = false)
    Instant clientTimestamp;

    @Column(name = "server_received_at", nullable = false)
    Instant serverReceivedAt;

    @Column(name = "device_info")
    String deviceInfo;

    protected MetadataSampleJpaEntity() {}

    static MetadataSampleJpaEntity from(MetadataSample s) {
        MetadataSampleJpaEntity e = new MetadataSampleJpaEntity();
        e.id = s.id();
        e.recordingId = s.recordingId();
        e.latitude = s.latitude();
        e.longitude = s.longitude();
        e.clientTimestamp = s.clientTimestamp();
        e.serverReceivedAt = s.serverReceivedAt();
        e.deviceInfo = s.deviceInfo();
        return e;
    }

    MetadataSample toDomain() {
        return new MetadataSample(id, recordingId, latitude, longitude, clientTimestamp,
                serverReceivedAt, deviceInfo);
    }
}
