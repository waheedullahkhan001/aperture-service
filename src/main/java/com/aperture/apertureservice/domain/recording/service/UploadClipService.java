package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.domain.recording.api.UploadClip;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@DomainService
public class UploadClipService implements UploadClip {

    private final EnsureRecording ensureRecording;
    private final Recordings recordings;
    private final RecordingSegments segments;
    private final SegmentFileStore files;

    public UploadClipService(EnsureRecording ensureRecording, Recordings recordings,
                             RecordingSegments segments, SegmentFileStore files) {
        this.ensureRecording = ensureRecording;
        this.recordings = recordings;
        this.segments = segments;
        this.files = files;
    }

    @Override
    @Transactional
    public RecordingSegment upload(UUID recordingId, UUID userId, InputStream data, String filename,
                                   long sizeHint, Instant startTime, Instant endTime,
                                   String quality, Integer segmentNumber) {
        // 1. Ensure recording exists and belongs to this user (creates if absent — pure-offline case)
        ensureRecording.ensure(recordingId, userId, startTime);

        // 2. Determine segment number
        int number = (segmentNumber != null) ? segmentNumber : segments.nextNumber(recordingId);

        // 3. Idempotency: if a segment with this (recordingId, number) already exists, return it
        var existing = segments.byNumber(recordingId, number);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 4. Store the file on disk
        String storedPath;
        try {
            storedPath = files.store(recordingId, filename, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded clip", e);
        }
        long size = files.sizeOf(storedPath);

        // 5. Insert segment record (source=UPLOADED, uploaded=true — file is available)
        RecordingSegment segment = new RecordingSegment(null, recordingId, number, storedPath,
                startTime, endTime, size, true, SegmentSource.UPLOADED, quality);
        segments.save(segment);

        // 6. Mark recording ENDED — offline recordings are always complete; live() covers PENDING
        recordings.byId(recordingId).ifPresent(r -> {
            if (r.live()) {
                recordings.save(r.ended(endTime));
            }
        });

        return segments.byNumber(recordingId, number).orElse(segment);
    }
}
