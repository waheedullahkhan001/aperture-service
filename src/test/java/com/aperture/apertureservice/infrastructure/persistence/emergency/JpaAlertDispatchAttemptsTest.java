package com.aperture.apertureservice.infrastructure.persistence.emergency;

import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import com.aperture.apertureservice.infrastructure.persistence.emergency.jpa.JpaAlertDispatchAttempts;
import com.aperture.apertureservice.support.JpaSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JpaSliceTest
@Import(JpaAlertDispatchAttempts.class)
class JpaAlertDispatchAttemptsTest {

    @Autowired
    JpaAlertDispatchAttempts attempts;

    @Test
    void recordsAndQueriesByOutcome() {
        UUID recordingId = UUID.randomUUID();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        attempts.record(new AlertDispatchAttempt(null, recordingId, 1L, t, false, "boom"));
        attempts.record(new AlertDispatchAttempt(null, recordingId, 1L, t.plusSeconds(5), true, null));
        attempts.record(new AlertDispatchAttempt(null, recordingId, 2L, t.plusSeconds(6), false, "boom"));

        assertThat(attempts.failedSince(t)).hasSize(2);
        assertThat(attempts.failedSince(t.plusSeconds(6))).hasSize(1);
        assertThat(attempts.countFor(recordingId, 1L)).isEqualTo(2);
        assertThat(attempts.anySuccess(recordingId, 1L)).isTrue();
        assertThat(attempts.anySuccess(recordingId, 2L)).isFalse();
    }
}
