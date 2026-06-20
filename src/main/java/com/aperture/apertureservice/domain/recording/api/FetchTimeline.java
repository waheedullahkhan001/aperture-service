package com.aperture.apertureservice.domain.recording.api;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Fetches (or returns a cached) muxed MP4 for the given recording's timeline window.
 * Authorization (viewSecret) is checked on every call so revoke takes effect immediately.
 *
 * @param start    ISO-8601 timestamp; null means "full recording" (resolved via MediaMTX /list)
 * @param duration seconds; null means "full recording"
 * @return absolute path to the cached MP4 file — serve this as a FileSystemResource
 */
public interface FetchTimeline {
    Path fetch(UUID recordingId, String viewSecret, String start, Double duration);
}
