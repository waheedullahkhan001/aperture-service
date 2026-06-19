package com.aperture.apertureservice.infrastructure.persistence.recording;

import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.infrastructure.persistence.recording.jpa.JpaRecordings;
import com.aperture.apertureservice.support.JpaSliceTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JpaSliceTest
@Import(JpaRecordings.class)
class JpaRecordingsTest {

    @Autowired
    JpaRecordings recordings;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private UUID seedUser() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        em.getEntityManager().createNativeQuery(
                "insert into users (id, email, fullname, password_hash, verified, created_at) " +
                "values (?1, ?2, 'U', 'h', false, now())")
                .setParameter(1, id)
                .setParameter(2, "u-" + id + "@example.com")
                .executeUpdate();
        return id;
    }

    private Recording recording(UUID userId, RecordingStatus status, Instant startedAt,
                                Instant countdownEndsAt, Instant dispatchedAt) {
        return new Recording(UuidCreator.getTimeOrderedEpoch(), userId, status, startedAt, null,
                "apv_" + UUID.randomUUID(), countdownEndsAt, dispatchedAt, false);
    }

    @Test
    void roundTripAndPagingAndStatusFilter() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        Recording older = recording(userId, RecordingStatus.ENDED, t.minusSeconds(60), null, null);
        Recording newer = recording(userId, RecordingStatus.RECORDING, t, null, null);
        recordings.save(older);
        recordings.save(newer);

        var page = recordings.byUser(userId, Optional.empty(), 0, 1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content().get(0).id()).isEqualTo(newer.id()); // newest first
        assertThat(recordings.byUser(userId, Optional.of(RecordingStatus.ENDED), 0, 10).content())
                .extracting(Recording::id).containsExactly(older.id());
        assertThat(recordings.idsByUser(userId)).hasSize(2);
    }

    @Test
    void dispatchDueSelectsOnlyDueUndispatchedLiveRows() {
        UUID userId = seedUser();
        Instant now = Instant.parse("2026-06-07T12:00:00Z");
        Recording due = recording(userId, RecordingStatus.RECORDING, now, now.minusSeconds(1), null);
        Recording notYet = recording(userId, RecordingStatus.RECORDING, now, now.plusSeconds(60), null);
        Recording done = recording(userId, RecordingStatus.RECORDING, now, now.minusSeconds(1), now);
        Recording noCountdown = recording(userId, RecordingStatus.RECORDING, now, null, null);
        Recording endedDue = recording(userId, RecordingStatus.ENDED, now, now.minusSeconds(1), null);
        for (Recording r : List.of(due, notYet, done, noCountdown, endedDue)) recordings.save(r);

        assertThat(recordings.dispatchDue(now)).extracting(Recording::id).containsExactly(due.id());
    }

    @Test
    void staleQueries() {
        UUID userId = seedUser();
        Instant now = Instant.parse("2026-06-07T12:00:00Z");
        Instant before = now.minus(Duration.ofMinutes(5));
        Recording oldPending = recording(userId, RecordingStatus.PENDING, now.minus(Duration.ofMinutes(10)), null, null);
        Recording oldStreamingNoSegments = recording(userId, RecordingStatus.RECORDING, now.minus(Duration.ofMinutes(10)), null, null);
        Recording oldStreamingFreshSegment = recording(userId, RecordingStatus.RECORDING, now.minus(Duration.ofMinutes(10)), null, null);
        for (Recording r : List.of(oldPending, oldStreamingNoSegments, oldStreamingFreshSegment)) recordings.save(r);
        em.getEntityManager().createNativeQuery(
                "insert into recording_segments (recording_id, segment_number, file_path, start_time, end_time, size_bytes, uploaded) " +
                "values (?1, 1, '/p', ?2, ?3, 1, true)")
                .setParameter(1, oldStreamingFreshSegment.id())
                .setParameter(2, now.minusSeconds(60))
                .setParameter(3, now.minusSeconds(30))
                .executeUpdate();

        assertThat(recordings.stalePending(before)).extracting(Recording::id).containsExactly(oldPending.id());
        assertThat(recordings.staleStreaming(before)).extracting(Recording::id)
                .containsExactly(oldStreamingNoSegments.id());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insertIfAbsentInsertsOnceThenReportsExisting() {
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into users (id, email, fullname, password_hash, verified, created_at) values (?,?,?,?,?,now())",
                userId, "race-slice-" + userId + "@example.com", "U", "h", false);
        Recording r = recording(userId, RecordingStatus.PENDING, Instant.parse("2026-06-07T12:00:00Z"), null, null);
        try {
            assertThat(recordings.insertIfAbsent(r)).isTrue();
            assertThat(recordings.insertIfAbsent(r)).isFalse();
            assertThat(recordings.byId(r.id())).isPresent();
        } finally {
            recordings.delete(r.id());
            jdbc.update("delete from users where id = ?", userId);
        }
    }
}
