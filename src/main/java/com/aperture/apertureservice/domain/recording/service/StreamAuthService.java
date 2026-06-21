package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.account.DeviceIdentity;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.WatchSegment;
import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.api.AuthorizePublish;
import com.aperture.apertureservice.domain.recording.api.AuthorizeView;
import com.aperture.apertureservice.domain.recording.api.GetWatchView;
import com.aperture.apertureservice.domain.recording.api.StreamWatchSegment;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@DomainService
public class StreamAuthService implements AuthorizePublish, AuthorizeView, GetWatchView, StreamWatchSegment {

    static final int WATCH_SAMPLES_LIMIT = 1000;

    private final IdentifyDevice identifyDevice;
    private final Recordings recordings;
    private final Users users;
    private final MetadataSamples samples;
    private final RecordingSegments segments;
    private final SegmentFileStore files;
    private final String hlsBase;
    private final String webrtcBase;

    public StreamAuthService(IdentifyDevice identifyDevice, Recordings recordings, Users users,
                             MetadataSamples samples, RecordingSegments segments, SegmentFileStore files,
                             String hlsBase, String webrtcBase) {
        this.identifyDevice = identifyDevice;
        this.recordings = recordings;
        this.users = users;
        this.samples = samples;
        this.segments = segments;
        this.files = files;
        this.hlsBase = hlsBase;
        this.webrtcBase = webrtcBase;
    }

    @Override
    public void authorizePublish(String deviceToken, UUID recordingId) {
        DeviceIdentity identity = identifyDevice.identify(deviceToken); // throws Unauthorized incl. revoked
        // absent row = the publish-start hook hasn't created it yet; allowed by design (parallel start)
        recordings.byId(recordingId).ifPresent(r -> {
            if (!r.userId().equals(identity.userId())) {
                throw new Forbidden("RECORDING_FORBIDDEN", "Recording belongs to another user");
            }
        });
    }

    @Override
    public void authorizeView(UUID recordingId, String viewSecret) {
        verifiedRow(recordingId, viewSecret);
    }

    @Override
    public WatchView watch(UUID recordingId, String viewSecret) {
        Recording r = verifiedRow(recordingId, viewSecret);
        String ownerName = users.byId(r.userId()).map(User::fullname).orElse("Unknown");
        List<WatchSegment> watchSegments = segments.byRecording(recordingId).stream()
                .sorted(Comparator.comparingInt(RecordingSegment::segmentNumber))
                .map(s -> new WatchSegment(s.segmentNumber(), s.startTime(), s.endTime(),
                        s.source() != null ? s.source().name() : "STREAMED", s.quality(), s.sizeBytes()))
                .toList();
        // recent() returns DESC; reverse to chronological ASC for the watch payload
        List<com.aperture.apertureservice.domain.recording.MetadataSample> recentDesc =
                samples.recent(recordingId, WATCH_SAMPLES_LIMIT);
        List<com.aperture.apertureservice.domain.recording.MetadataSample> chronological =
                recentDesc.reversed();
        return new WatchView(ownerName, r.startedAt(), r.status(), samples.latest(recordingId),
                hlsBase + "/aperture/" + recordingId + "/index.m3u8?t=" + r.viewSecret(),
                webrtcBase + "/aperture/" + recordingId + "/whep?t=" + r.viewSecret(),
                watchSegments, chronological);
    }

    @Override
    public SegmentDownload stream(UUID recordingId, int segmentNumber, String viewSecret) {
        verifiedRow(recordingId, viewSecret);
        var segment = segments.byNumber(recordingId, segmentNumber)
                .orElseThrow(() -> new NotFound("SEGMENT_NOT_FOUND", "Segment not found"));
        String filename = "segment-" + segmentNumber + ".mp4";
        return files.open(segment.filePath(), filename);
    }

    private Recording verifiedRow(UUID recordingId, String viewSecret) {
        Recording r = recordings.byId(recordingId)
                .orElseThrow(() -> new NotFound("RECORDING_NOT_FOUND", "Recording not found"));
        if (r.viewRevoked()) {
            throw new Forbidden("VIEW_REVOKED", "This watch link has been revoked");
        }
        byte[] expected = r.viewSecret().getBytes(StandardCharsets.UTF_8);
        byte[] presented = (viewSecret == null ? "" : viewSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, presented)) {
            throw new Forbidden("INVALID_VIEW_TOKEN", "Invalid view token");
        }
        return r;
    }
}
