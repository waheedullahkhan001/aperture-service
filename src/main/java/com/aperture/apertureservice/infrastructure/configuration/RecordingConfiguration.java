package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.api.EnsureRecording;
import com.aperture.apertureservice.domain.recording.service.UploadClipService;
import com.aperture.apertureservice.domain.recording.service.LibraryService;
import com.aperture.apertureservice.domain.recording.service.RecordingService;
import com.aperture.apertureservice.domain.recording.service.StreamAuthService;
import com.aperture.apertureservice.domain.recording.service.TelemetryService;
import com.aperture.apertureservice.domain.recording.spi.AlertPolicy;
import com.aperture.apertureservice.domain.recording.spi.MetadataSamples;
import com.aperture.apertureservice.domain.recording.spi.RecordingSegments;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;
import com.aperture.apertureservice.ddd.RandomTokens;
import com.aperture.apertureservice.infrastructure.persistence.filestore.LocalFsSegmentFileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RecordingConfiguration {

    @Bean
    SegmentFileStore segmentFileStore(AppProperties props) {
        return new LocalFsSegmentFileStore(props.recordingsPath());
    }

    @Bean
    RecordingService recordingService(Recordings recordings, AlertPolicy alertPolicy,
                                      RandomTokens tokens, Clock clock) {
        return new RecordingService(recordings, alertPolicy, tokens, clock);
    }

    @Bean
    StreamAuthService streamAuthService(IdentifyDevice identifyDevice, Recordings recordings,
                                        Users users, MetadataSamples samples,
                                        RecordingSegments segments, SegmentFileStore files,
                                        AppProperties props) {
        return new StreamAuthService(identifyDevice, recordings, users, samples, segments, files,
                props.streaming().hlsBase(), props.streaming().webrtcBase());
    }

    @Bean
    TelemetryService telemetryService(Recordings recordings, RecordingSegments segments,
                                      MetadataSamples samples, SegmentFileStore files, Clock clock) {
        return new TelemetryService(recordings, segments, samples, files, clock);
    }

    @Bean
    LibraryService libraryService(Recordings recordings, RecordingSegments segments,
                                  MetadataSamples samples, SegmentFileStore files) {
        return new LibraryService(recordings, segments, samples, files);
    }

    @Bean
    UploadClipService uploadClipService(EnsureRecording ensureRecording, Recordings recordings,
                                        RecordingSegments segments, SegmentFileStore files) {
        return new UploadClipService(ensureRecording, recordings, segments, files);
    }
}
