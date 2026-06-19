package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.ddd.PageOf;
import com.aperture.apertureservice.domain.recording.MetadataSample;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingSegment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class RecordingDtos {
    private RecordingDtos() {}

    public record RecordingResponse(UUID id, String status, Instant startedAt, Instant endedAt,
                                    Instant countdownEndsAt, Instant alertsDispatchedAt) {
        public static RecordingResponse from(Recording r) {
            return new RecordingResponse(r.id(), r.status().name(), r.startedAt(), r.endedAt(),
                    r.countdownEndsAt(), r.alertsDispatchedAt());
        }
    }

    public record SegmentResponse(int segmentNumber, Instant startTime, Instant endTime, long sizeBytes,
                                  String source, String quality) {
        public static SegmentResponse from(RecordingSegment s) {
            return new SegmentResponse(s.segmentNumber(), s.startTime(), s.endTime(), s.sizeBytes(),
                    s.source() != null ? s.source().name() : "STREAMED", s.quality());
        }
    }

    public record SampleResponse(BigDecimal latitude, BigDecimal longitude, Instant clientTimestamp,
                                 String deviceInfo, Double horizontalAccuracyM, Double speedMps,
                                 Double bearingDeg, Double altitudeM, Integer batteryPercent) {
        public static SampleResponse from(MetadataSample s) {
            return new SampleResponse(s.latitude(), s.longitude(), s.clientTimestamp(), s.deviceInfo(),
                    s.horizontalAccuracyM(), s.speedMps(), s.bearingDeg(), s.altitudeM(), s.batteryPercent());
        }
    }

    public record DetailResponse(RecordingResponse recording, List<SegmentResponse> segments,
                                 List<SampleResponse> recentSamples, String watchUrl) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <S, T> PageResponse<T> from(PageOf<S> page, Function<S, T> mapper) {
            return new PageResponse<>(page.content().stream().map(mapper).toList(),
                    page.page(), page.size(), page.totalElements(), page.totalPages());
        }
    }
}
