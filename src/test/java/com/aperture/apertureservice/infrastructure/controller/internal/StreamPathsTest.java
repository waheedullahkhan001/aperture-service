package com.aperture.apertureservice.infrastructure.controller.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StreamPathsTest {

    @Test
    void extractsRecordingIdFromAperturePath() {
        UUID id = UUID.randomUUID();
        assertThat(StreamPaths.recordingId("aperture/" + id)).contains(id);
        assertThat(StreamPaths.recordingId("other/" + id)).isEmpty();
        assertThat(StreamPaths.recordingId("aperture/not-a-uuid")).isEmpty();
        assertThat(StreamPaths.recordingId(null)).isEmpty();
    }

    @Test
    void extractsQueryParams() {
        assertThat(StreamPaths.queryParam("token=apd_x&y=1", "token")).contains("apd_x");
        assertThat(StreamPaths.queryParam("t=apv%5Fz", "t")).contains("apv_z"); // url-decoded
        assertThat(StreamPaths.queryParam("", "token")).isEmpty();
        assertThat(StreamPaths.queryParam(null, "token")).isEmpty();
    }

    @Test
    void parsesDurations() {
        assertThat(StreamPaths.durationSeconds("30")).isEqualTo(30.0);
        assertThat(StreamPaths.durationSeconds("12.5s")).isEqualTo(12.5);
        assertThat(StreamPaths.durationSeconds("garbage")).isEqualTo(0.0);
        assertThat(StreamPaths.durationSeconds(null)).isEqualTo(0.0);
    }
}
