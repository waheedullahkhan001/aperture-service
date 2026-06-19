package com.aperture.apertureservice.infrastructure.persistence.filestore;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import com.aperture.apertureservice.domain.recording.spi.SegmentFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class LocalFsSegmentFileStore implements SegmentFileStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFsSegmentFileStore.class);

    private final Path root;

    public LocalFsSegmentFileStore(String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    private Path insideRoot(String filePath) {
        Path p = Path.of(filePath).toAbsolutePath().normalize();
        if (!p.startsWith(root)) {
            throw new Forbidden("PATH_FORBIDDEN", "Path outside the recordings root");
        }
        return p;
    }

    @Override
    public long sizeOf(String filePath) {
        try {
            Path p = Path.of(filePath).toAbsolutePath().normalize();
            if (!p.startsWith(root) || !Files.exists(p)) {
                return 0;
            }
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public SegmentDownload open(String filePath, String downloadFilename) {
        Path p = insideRoot(filePath);
        try {
            long size = Files.size(p);
            return new SegmentDownload(Files.newInputStream(p), size, downloadFilename);
        } catch (IOException e) {
            throw new NotFound("SEGMENT_FILE_MISSING", "Segment file missing");
        }
    }

    /** Best-effort by design: a leaked file with a deleted row is accepted (logged), never blocking. */
    @Override
    public void delete(String filePath) {
        Path p = insideRoot(filePath);
        try {
            Files.deleteIfExists(p);
            Path parent = p.getParent();
            if (parent != null && !parent.equals(root) && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not delete segment file {}: {}", filePath, e.toString());
        }
    }

    @Override
    public String store(UUID recordingId, String filename, InputStream data) throws IOException {
        Path dir = root.resolve("aperture").resolve(recordingId.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new Forbidden("PATH_FORBIDDEN", "Path outside the recordings root");
        }
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }
}
