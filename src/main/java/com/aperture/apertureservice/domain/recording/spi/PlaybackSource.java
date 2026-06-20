package com.aperture.apertureservice.domain.recording.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Port for fetching muxed MP4 data from the MediaMTX playback API.
 * The infrastructure adapter (MediaMtxPlaybackClient) makes the real HTTP call;
 * test stubs return canned data without any network.
 */
public interface PlaybackSource {

    record Span(String start, double duration) {}

    /**
     * Returns the muxed MP4 stream for the given recording path and time range.
     * Caller is responsible for closing the stream.
     *
     * @param path            MediaMTX path, e.g. "aperture/&lt;uuid&gt;"
     * @param startRfc3339    ISO-8601 instant string, e.g. "2026-06-19T10:00:00Z"
     * @param durationSeconds recording duration in seconds
     * @param viewSecret      the recording's view secret (passed as MediaMTX auth token)
     */
    InputStream fetch(String path, String startRfc3339, double durationSeconds,
                      String viewSecret) throws IOException;

    /**
     * Returns the list of recorded spans for the given path (MediaMTX /list endpoint).
     * Used to resolve the full start+duration for a recording when no clip params are given.
     */
    List<Span> list(String path, String viewSecret) throws IOException;
}
