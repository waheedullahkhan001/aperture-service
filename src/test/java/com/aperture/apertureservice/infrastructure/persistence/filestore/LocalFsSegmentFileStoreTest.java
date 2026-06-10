package com.aperture.apertureservice.infrastructure.persistence.filestore;

import com.aperture.apertureservice.ddd.Forbidden;
import com.aperture.apertureservice.ddd.NotFound;
import com.aperture.apertureservice.domain.recording.SegmentDownload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFsSegmentFileStoreTest {

    @TempDir Path root;

    private LocalFsSegmentFileStore store;

    @BeforeEach
    void setUp() { store = new LocalFsSegmentFileStore(root.toString()); }

    @Test
    void sizeOpenDeleteRoundTrip() throws Exception {
        Path dir = Files.createDirectories(root.resolve("aperture/rec1"));
        Path file = Files.write(dir.resolve("a.mp4"), new byte[]{1, 2, 3});

        assertThat(store.sizeOf(file.toString())).isEqualTo(3);
        SegmentDownload dl = store.open(file.toString(), "rec1-1.mp4");
        assertThat(dl.stream().readAllBytes()).containsExactly(1, 2, 3);
        assertThat(dl.sizeBytes()).isEqualTo(3);

        store.delete(file.toString());
        assertThat(Files.exists(file)).isFalse();
        assertThat(Files.exists(dir)).isFalse();   // empty parent pruned
    }

    @Test
    void missingFileSizeZeroOpenNotFound() {
        assertThat(store.sizeOf(root.resolve("nope.mp4").toString())).isZero();
        assertThatThrownBy(() -> store.open(root.resolve("nope.mp4").toString(), "x.mp4"))
                .isInstanceOf(NotFound.class);
    }

    @Test
    void refusesPathsOutsideRoot() {
        assertThatThrownBy(() -> store.open("/etc/passwd", "x"))
                .isInstanceOf(Forbidden.class).hasFieldOrPropertyWithValue("code", "PATH_FORBIDDEN");
        assertThatThrownBy(() -> store.delete(root + "/../escape.mp4"))
                .isInstanceOf(Forbidden.class);
        assertThat(store.sizeOf("/etc/passwd")).isZero(); // sizeOf is lenient but never reads outside
    }
}
