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

    @Column(name = "horizontal_accuracy_m")
    Double horizontalAccuracyM;

    @Column(name = "speed_mps")
    Double speedMps;

    @Column(name = "bearing_deg")
    Double bearingDeg;

    @Column(name = "altitude_m")
    Double altitudeM;

    @Column(name = "battery_percent")
    Integer batteryPercent;

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
        e.horizontalAccuracyM = s.horizontalAccuracyM();
        e.speedMps = s.speedMps();
        e.bearingDeg = s.bearingDeg();
        e.altitudeM = s.altitudeM();
        e.batteryPercent = s.batteryPercent();
        return e;
    }

    MetadataSample toDomain() {
        return new MetadataSample(id, recordingId, latitude, longitude, clientTimestamp,
                serverReceivedAt, deviceInfo, horizontalAccuracyM, speedMps, bearingDeg,
                altitudeM, batteryPercent);
    }
}
