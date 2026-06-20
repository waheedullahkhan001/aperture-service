package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.spi.PlaybackSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * In-memory stub for PlaybackSource. Tests configure it before each scenario.
 * fetchBytes: the bytes returned by fetch(); default is a minimal fake MP4 payload.
 * spans: the list returned by list(); default is a single 60-second span.
 */
@Stub
public class StubPlaybackSource implements PlaybackSource {

    private byte[] fetchBytes = "FAKE-MP4-BYTES".getBytes();
    private List<Span> spans = List.of(new Span("2026-06-19T10:00:00Z", 60.0));

    public void setFetchBytes(byte[] bytes) { this.fetchBytes = bytes; }
    public void setSpans(List<Span> spans) { this.spans = spans; }

    @Override
    public InputStream fetch(String path, String startRfc3339, double durationSeconds,
                             String viewSecret) {
        return new ByteArrayInputStream(fetchBytes);
    }

    @Override
    public List<Span> list(String path, String viewSecret) {
        return spans;
    }
}
