package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingDetail;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.api.DeleteRecording;
import com.aperture.apertureservice.domain.recording.api.DownloadSegment;
import com.aperture.apertureservice.domain.recording.api.GetRecording;
import com.aperture.apertureservice.domain.recording.api.ListRecordings;
import com.aperture.apertureservice.domain.recording.api.RevokeWatchLink;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@DomainService
public class LibraryService implements ListRecordings, GetRecording, DownloadSegment, DeleteRecording, RevokeWatchLink {

    static final int RECENT_SAMPLES = 20;

    private final Recordings recordings;
    private final RecordingSegments segments;
    private final MetadataSamples samples;
    private final SegmentFileStore files;

    public LibraryService(Recordings recordings, RecordingSegments segments,
                          MetadataSamples samples, SegmentFileStore files) {
        this.recordings = recordings;
        this.segments = segments;
        this.samples = samples;
        this.files = files;
    }

    @Override
    public PageOf<Recording> list(UUID userId, Optional<RecordingStatus> status, int page, int size) {
        return recordings.byUser(userId, status, Math.max(0, page), Math.clamp(size, 1, 100));
    }

    @Override
    public RecordingDetail get(UUID userId, UUID recordingId) {
        Recording r = owned(userId, recordingId);
        return new RecordingDetail(r, segments.byRecording(recordingId),
                samples.recent(recordingId, RECENT_SAMPLES));
    }

    @Override
    public SegmentDownload download(UUID userId, UUID recordingId, int segmentNumber) {
        owned(userId, recordingId);
        RecordingSegment segment = segments.byNumber(recordingId, segmentNumber)
                .orElseThrow(() -> new NotFound("SEGMENT_NOT_FOUND", "Segment not found"));
        return files.open(segment.filePath(), recordingId + "-" + segmentNumber + ".mp4");
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID recordingId) {
        owned(userId, recordingId);
        segments.byRecording(recordingId).forEach(s -> files.delete(s.filePath()));
        segments.deleteFor(recordingId);
        samples.deleteFor(recordingId);
        recordings.delete(recordingId);
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID recordingId) {
        Recording r = recordings.byIdForUpdate(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!r.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        if (!r.viewRevoked()) {
            recordings.save(r.revokedView());
        }
    }

    private Recording owned(UUID userId, UUID recordingId) {
        Recording r = recordings.byId(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!r.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        return r;
    }
}
