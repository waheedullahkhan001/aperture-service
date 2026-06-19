package com.aperture.apertureservice.infrastructure.controller.publicapi;

import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.WatchSegment;
import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.api.GetWatchView;
import com.aperture.apertureservice.domain.recording.api.StreamWatchSegment;
import com.aperture.apertureservice.infrastructure.controller.web.RecordingDtos;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public record WatchResponse(String ownerName, Instant startedAt, String status,
                                RecordingDtos.SampleResponse latestSample, String hlsUrl, String webrtcUrl,
                                List<WatchSegmentDto> segments) {}

    @GetMapping("/{id}")
    public WatchResponse watch(@PathVariable UUID id, @RequestParam("t") String token) {
        WatchView v = getWatchView.watch(id, token);
        List<WatchSegmentDto> segmentDtos = v.segments().stream().map(WatchSegmentDto::from).toList();
        return new WatchResponse(v.ownerName(), v.startedAt(), v.status().name(),
                v.latestSample().map(RecordingDtos.SampleResponse::from).orElse(null),
                v.hlsUrl(), v.webrtcUrl(), segmentDtos);
    }

    @GetMapping("/{id}/segments/{n}")
    public ResponseEntity<InputStreamResource> streamSegment(@PathVariable UUID id,
                                                              @PathVariable int n,
                                                              @RequestParam("t") String token) {
        SegmentDownload dl = streamWatchSegment.stream(id, n, token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + dl.filename() + "\"")
                .contentLength(dl.sizeBytes())
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(new InputStreamResource(dl.stream()));
    }
}
