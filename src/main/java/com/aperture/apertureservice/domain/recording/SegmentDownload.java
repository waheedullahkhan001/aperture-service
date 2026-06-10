package com.aperture.apertureservice.domain.recording;

import java.io.InputStream;

public record SegmentDownload(InputStream stream, long sizeBytes, String filename) {}
