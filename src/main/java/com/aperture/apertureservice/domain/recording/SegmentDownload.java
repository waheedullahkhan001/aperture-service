package com.aperture.apertureservice.domain.recording;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Carries both a stream (for streaming/attachment download) and the backing file path
 * (so the caller can serve a FileSystemResource for Range-capable video playback).
 * filePath may be null for in-memory sources (tests, stubs) — callers that need Range
 * support must not receive a null path; the real LocalFsSegmentFileStore always sets it.
 */
public record SegmentDownload(InputStream stream, long sizeBytes, String filename, Path filePath) {
    /** Compatibility constructor for callers that do not have a file path (stubs, older code). */
    public SegmentDownload(InputStream stream, long sizeBytes, String filename) {
        this(stream, sizeBytes, filename, null);
    }
}
