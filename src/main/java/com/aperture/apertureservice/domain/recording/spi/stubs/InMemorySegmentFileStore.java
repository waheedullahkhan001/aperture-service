package com.aperture.apertureservice.domain.recording.spi.stubs;

import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Stub
public class InMemorySegmentFileStore implements SegmentFileStore {
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    public void put(String path, byte[] content) { files.put(path, content); }
    public boolean exists(String path) { return files.containsKey(path); }

    @Override public long sizeOf(String filePath) {
        byte[] b = files.get(filePath);
        return b == null ? 0 : b.length;
    }

    @Override public SegmentDownload open(String filePath, String downloadFilename) {
        byte[] b = files.get(filePath);
        if (b == null) throw new NotFound("SEGMENT_FILE_MISSING", "Segment file missing");
        return new SegmentDownload(new ByteArrayInputStream(b), b.length, downloadFilename);
    }

    @Override public void delete(String filePath) { files.remove(filePath); }

    @Override public String store(UUID recordingId, String filename, InputStream data) throws IOException {
        byte[] bytes = data.readAllBytes();
        String path = "/recordings/aperture/" + recordingId + "/" + filename;
        files.put(path, bytes);
        return path;
    }
}
