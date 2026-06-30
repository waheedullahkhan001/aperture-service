package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
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
                                   String quality, String clipId) {
        // 1. Ensure recording exists and belongs to this user (creates if absent — pure-offline case)
        ensureRecording.ensure(recordingId, userId, startTime, null);

        // 2. Idempotency: if a segment with this clientClipId already exists, return it.
        //    Keying on clientClipId (not segmentNumber) prevents a collision where a streamed
        //    segment already occupies the phone-supplied number (the original bug).
        var existing = segments.byClientClipId(recordingId, clipId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 3. Server-assign segmentNumber — guarantees uniqueness within the recording regardless
        //    of what the streaming path has already consumed.
        int number = segments.nextNumber(recordingId);

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
                startTime, endTime, size, true, SegmentSource.UPLOADED, quality, clipId);
        segments.save(segment);

        // 6. Complete a never-streamed (PENDING) recording — a pure-offline recording is finished
        // when its clips arrive. CRUCIALLY do NOT end a RECORDING one: clips also stream in
        // mid-recording at reconnect (SRS-036) while an emergency is still live, and the streaming
        // lifecycle (publish-end) owns ending those. Ending a live recording here would prematurely
        // mark an in-progress emergency as ENDED.
        recordings.byId(recordingId).ifPresent(r -> {
            if (r.status() == RecordingStatus.PENDING) {
                recordings.save(r.ended(endTime));
            }
        });

        return segments.byClientClipId(recordingId, clipId).orElse(segment);
    }
}
