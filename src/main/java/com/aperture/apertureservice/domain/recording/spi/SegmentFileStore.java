package com.aperture.apertureservice.domain.recording.spi;

import com.aperture.apertureservice.domain.recording.SegmentDownload;

public interface SegmentFileStore {
    long sizeOf(String filePath);                       // 0 if missing
    SegmentDownload open(String filePath, String downloadFilename); // NotFound if missing
    void delete(String filePath);                       // best-effort, also prunes empty parent dir
}
