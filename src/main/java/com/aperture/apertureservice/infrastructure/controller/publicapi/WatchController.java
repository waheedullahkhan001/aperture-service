package com.aperture.apertureservice.infrastructure.controller.publicapi;

import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.WatchSegment;
import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.api.GetWatchView;
import com.aperture.apertureservice.domain.recording.api.StreamWatchSegment;
import com.aperture.apertureservice.infrastructure.controller.web.RecordingDtos;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/watch")
public class WatchController {

    private final GetWatchView getWatchView;
    private final StreamWatchSegment streamWatchSegment;

    public WatchController(GetWatchView getWatchView, StreamWatchSegment streamWatchSegment) {
        this.getWatchView = getWatchView;
        this.streamWatchSegment = streamWatchSegment;
    }

    public record WatchSegmentDto(int segmentNumber, Instant startTime, Instant endTime,
                                  String source, String quality, long sizeBytes) {
        static WatchSegmentDto from(WatchSegment s) {
            return new WatchSegmentDto(s.segmentNumber(), s.startTime(), s.endTime(),
                    s.source(), s.quality(), s.sizeBytes());
        }
    }

    /**
     * Public-safe telemetry sample: exposes only the fields a guest viewer needs.
     * Deliberately excludes id, recordingId, and serverReceivedAt (internal fields).
     */
    public record WatchSampleDto(BigDecimal latitude, BigDecimal longitude, Instant clientTimestamp,
                                 String deviceInfo, Double horizontalAccuracyM, Double speedMps,
                                 Double bearingDeg, Double altitudeM, Integer batteryPercent) {
        static WatchSampleDto from(MetadataSample s) {
            return new WatchSampleDto(s.latitude(), s.longitude(), s.clientTimestamp(), s.deviceInfo(),
                    s.horizontalAccuracyM(), s.speedMps(), s.bearingDeg(), s.altitudeM(), s.batteryPercent());
        }
    }

    public record WatchResponse(String ownerName, Instant startedAt, String status,
                                RecordingDtos.SampleResponse latestSample, String hlsUrl, String webrtcUrl,
                                List<WatchSegmentDto> segments, List<WatchSampleDto> samples,
                                String deviceName) {}

    @GetMapping("/{id}")
    public WatchResponse watch(@PathVariable UUID id, @RequestParam("t") String token) {
        WatchView v = getWatchView.watch(id, token);
        List<WatchSegmentDto> segmentDtos = v.segments().stream().map(WatchSegmentDto::from).toList();
        List<WatchSampleDto> sampleDtos = v.samples().stream().map(WatchSampleDto::from).toList();
        return new WatchResponse(v.ownerName(), v.startedAt(), v.status().name(),
                v.latestSample().map(RecordingDtos.SampleResponse::from).orElse(null),
                v.hlsUrl(), v.webrtcUrl(), segmentDtos, sampleDtos, v.deviceName());
    }

    @GetMapping("/{id}/segments/{n}")
    public ResponseEntity<Resource> streamSegment(@PathVariable UUID id,
                                                  @PathVariable int n,
                                                  @RequestParam("t") String token) {
        SegmentDownload dl = streamWatchSegment.stream(id, n, token);
        // Use FileSystemResource when backed by a real file — Spring MVC then handles
        // Range requests (206) automatically. Fall back to InputStreamResource for
        // in-memory stubs that have no file path.
        Resource resource = dl.filePath() != null
                ? new FileSystemResource(dl.filePath())
                : new InputStreamResource(dl.stream());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }
}
