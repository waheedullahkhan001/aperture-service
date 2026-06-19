package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.BadRequest;
import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.SegmentSource;
import com.aperture.apertureservice.domain.recording.api.AppendMetadataSamples;
import com.aperture.apertureservice.domain.recording.api.RecordSegmentNotification;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DomainService
public class TelemetryService implements AppendMetadataSamples, RecordSegmentNotification {

    static final int MAX_BATCH = 500;

    private final Recordings recordings;
    private final RecordingSegments segments;
    private final MetadataSamples samples;
    private final SegmentFileStore files;
    private final Clock clock;

    public TelemetryService(Recordings recordings, RecordingSegments segments, MetadataSamples samples,
                            SegmentFileStore files, Clock clock) {
        this.recordings = recordings;
        this.segments = segments;
        this.samples = samples;
        this.files = files;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int append(UUID recordingId, UUID userId, List<NewSample> newSamples) {
        Recording r = recordings.byId(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (!r.userId().equals(userId)) {
            throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
        }
        if (newSamples.size() > MAX_BATCH) {
            throw new BadRequest("BATCH_TOO_LARGE", "At most " + MAX_BATCH + " samples per batch");
        }
        Instant now = clock.instant();
        samples.saveAll(newSamples.stream()
                .map(s -> new MetadataSample(null, recordingId, s.latitude(), s.longitude(),
                        s.clientTimestamp() != null ? s.clientTimestamp() : now, now, s.deviceInfo(),
                        s.horizontalAccuracyM(), s.speedMps(), s.bearingDeg(), s.altitudeM(), s.batteryPercent()))
                .toList());
        return newSamples.size();
    }

    @Override
    @Transactional
    public void segmentCompleted(UUID recordingId, String segmentPath, double durationSeconds) {
        if (recordings.byId(recordingId).isEmpty()) {
            return; // unknown recording — hook noise, ignore
        }
        if (segments.existsForPath(recordingId, segmentPath)) {
            return; // duplicate hook delivery
        }
        Instant end = clock.instant();
        Instant start = end.minusMillis(Math.round(Math.max(0, durationSeconds) * 1000));
        segments.save(new RecordingSegment(null, recordingId, segments.nextNumber(recordingId),
                segmentPath, start, end, files.sizeOf(segmentPath), true,
                // source=STREAMED (origin); uploaded=true means file available — separate concerns
                SegmentSource.STREAMED, null, null));
    }
}
