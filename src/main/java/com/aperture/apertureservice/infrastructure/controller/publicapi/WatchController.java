package com.aperture.apertureservice.infrastructure.controller.publicapi;

import com.aperture.apertureservice.domain.recording.WatchView;
import com.aperture.apertureservice.domain.recording.api.GetWatchView;
import com.aperture.apertureservice.infrastructure.controller.web.RecordingDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/watch")
public class WatchController {

    private final GetWatchView getWatchView;

    public WatchController(GetWatchView getWatchView) {
        this.getWatchView = getWatchView;
    }

    public record WatchResponse(String ownerName, Instant startedAt, String status,
                                RecordingDtos.SampleResponse latestSample, String hlsUrl, String webrtcUrl) {}

    @GetMapping("/{id}")
    public WatchResponse watch(@PathVariable UUID id, @RequestParam("t") String token) {
        WatchView v = getWatchView.watch(id, token);
        return new WatchResponse(v.ownerName(), v.startedAt(), v.status().name(),
                v.latestSample().map(RecordingDtos.SampleResponse::from).orElse(null),
                v.hlsUrl(), v.webrtcUrl());
    }
}
