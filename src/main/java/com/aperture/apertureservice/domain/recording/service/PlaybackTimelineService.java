package com.aperture.apertureservice.domain.recording.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.api.FetchTimeline;
import com.aperture.apertureservice.domain.recording.spi.PlaybackSource;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@DomainService
public class PlaybackTimelineService implements FetchTimeline {

    private static final Logger log = LoggerFactory.getLogger(PlaybackTimelineService.class);

    private final Recordings recordings;
    private final PlaybackSource playback;
    private final Path cacheRoot; // <recordingsPath>/_playcache

    public PlaybackTimelineService(Recordings recordings, PlaybackSource playback, String recordingsPath) {
        this.recordings = recordings;
        this.playback = playback;
        this.cacheRoot = Path.of(recordingsPath).toAbsolutePath().normalize().resolve("_playcache");
    }

    @Override
    public Path fetch(UUID recordingId, String viewSecret, String start, Double duration) {
        // 1. Authorize — check viewSecret constant-time, then check revoke
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

        // 2. Resolve time range
        String resolvedStart;
        double resolvedDuration;
        String mtxPath = "aperture/" + recordingId;

        if (start != null && duration != null) {
            resolvedStart = start;
            resolvedDuration = duration;
        } else {
            // Full recording: call MediaMTX /list for the first span
            List<PlaybackSource.Span> spans;
            try {
                spans = playback.list(mtxPath, r.viewSecret());
            } catch (IOException e) {
                throw new RuntimeException("Failed to list spans from MediaMTX", e);
            }
            if (spans.isEmpty()) {
                throw new NotFound("NO_SPANS", "No recorded spans available for this recording");
            }
            PlaybackSource.Span span = spans.get(0);
            resolvedStart = span.start();
            resolvedDuration = span.duration();
        }

        // 3. Cache key: <id>/<sanitized-start>_<duration>.mp4
        String cacheKey = sanitize(resolvedStart) + "_" + resolvedDuration;
        Path cacheDir = cacheRoot.resolve(recordingId.toString());
        Path cacheFile = cacheDir.resolve(cacheKey + ".mp4");

        if (Files.exists(cacheFile) && isNonEmpty(cacheFile)) {
            log.debug("Timeline cache hit: {}", cacheFile);
            return cacheFile;
        }

        // 4. Fetch from MediaMTX and write atomically
        log.debug("Timeline cache miss — fetching from MediaMTX: path={} start={} duration={}",
                mtxPath, resolvedStart, resolvedDuration);
        try {
            Files.createDirectories(cacheDir);
            Path tmp = cacheDir.resolve(cacheKey + ".tmp");
            try (InputStream stream = playback.fetch(mtxPath, resolvedStart, resolvedDuration, r.viewSecret())) {
                Files.copy(stream, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch timeline from MediaMTX", e);
        }

        return cacheFile;
    }

    private static boolean isNonEmpty(Path p) {
        try { return Files.size(p) > 0; } catch (IOException e) { return false; }
    }

    /** Replace characters that are unsafe in filenames. */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
