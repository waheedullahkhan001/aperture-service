package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_segments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recording_id", "segment_number"}))
class SegmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "recording_id", nullable = false)
    UUID recordingId;

    @Column(name = "segment_number", nullable = false)
    int segmentNumber;

    @Column(name = "file_path", nullable = false, length = 512)
    String filePath;

    @Column(name = "start_time", nullable = false)
    Instant startTime;

    @Column(name = "end_time", nullable = false)
    Instant endTime;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(nullable = false)
    boolean uploaded;

    @Column(name = "source", nullable = false, length = 16)
    String source;

    @Column(name = "quality")
    String quality;

    @Column(name = "client_clip_id")
    String clientClipId;

    protected SegmentJpaEntity() {}

    static SegmentJpaEntity from(RecordingSegment s) {
        SegmentJpaEntity e = new SegmentJpaEntity();
        e.id = s.id();
        e.recordingId = s.recordingId();
        e.segmentNumber = s.segmentNumber();
        e.filePath = s.filePath();
        e.startTime = s.startTime();
        e.endTime = s.endTime();
        e.sizeBytes = s.sizeBytes();
        e.uploaded = s.uploaded();
        e.source = s.source() != null ? s.source().name() : SegmentSource.STREAMED.name();
        e.quality = s.quality();
        e.clientClipId = s.clientClipId();
        return e;
    }

    RecordingSegment toDomain() {
        return new RecordingSegment(id, recordingId, segmentNumber, filePath, startTime, endTime,
                sizeBytes, uploaded,
                source != null ? SegmentSource.valueOf(source) : SegmentSource.STREAMED,
                quality, clientClipId);
    }
}
