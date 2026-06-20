package com.aperture.apertureservice.domain.recording.spi;

import com.aperture.apertureservice.domain.recording.SegmentDownload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface SegmentFileStore {
    long sizeOf(String filePath);                        // 0 if missing
    SegmentDownload open(String filePath, String downloadFilename); // NotFound if missing
    void delete(String filePath);                        // best-effort, also prunes empty parent dir
    /** Stores an uploaded file under <root>/aperture/<id>/<filename>, path-confined. Returns absolute path. */
    String store(UUID recordingId, String filename, InputStream data) throws IOException;
    /** Returns the absolute, root-confined Path for the given stored path. Throws Forbidden if outside root, NotFound if file missing. */
    Path pathOf(String filePath);
}
