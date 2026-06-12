package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Uses a portable INSERT...WHERE NOT EXISTS to perform an idempotent insert.
 * This avoids poisoning the JPA EntityManager / Hibernate session with a
 * PersistenceException on constraint violation, which would mark the transaction
 * rollback-only even when the exception is caught.
 *
 * H2 2.4 rejects ON CONFLICT even in PostgreSQL mode, so we use
 * INSERT...WHERE NOT EXISTS instead. The NOT EXISTS check and the insert are not
 * atomic, so two truly concurrent threads with the same id can both pass the
 * check; the loser then hits a DuplicateKeyException, which we catch here and
 * convert to {@code false}. This is safe because: (a) we are in REQUIRES_NEW —
 * plain JDBC, no Hibernate session to poison; (b) Postgres treats COMMIT on an
 * aborted transaction as a rollback, harmlessly. The conflict target is (id)
 * only; a view_secret collision (256-bit random) is left to surface as
 * created=false -> NotFound upstream — acceptable.
 *
 * REQUIRES_NEW ensures the insert is committed (or silently skipped) before the
 * caller's transaction proceeds, regardless of whether a surrounding transaction exists.
 */
@Component
class RecordingInsertOperationsImpl implements RecordingInsertOperations {

    private final JdbcClient jdbc;

    RecordingInsertOperationsImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertIfAbsent(RecordingJpaEntity entity) {
        try {
            int rows = jdbc.sql("""
                    INSERT INTO recordings
                        (id, user_id, status, started_at, ended_at, view_secret,
                         countdown_ends_at, alerts_dispatched_at)
                    SELECT :id, :userId, :status, :startedAt, :endedAt, :viewSecret,
                           :countdownEndsAt, :alertsDispatchedAt
                    WHERE NOT EXISTS (SELECT 1 FROM recordings WHERE id = :id)
                    """)
                    .param("id", entity.id)
                    .param("userId", entity.userId)
                    .param("status", entity.status)
                    .param("startedAt", toTs(entity.startedAt))
                    .param("endedAt", toTs(entity.endedAt))
                    .param("viewSecret", entity.viewSecret)
                    .param("countdownEndsAt", toTs(entity.countdownEndsAt))
                    .param("alertsDispatchedAt", toTs(entity.alertsDispatchedAt))
                    .update();
            return rows == 1;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Race loser: the row appeared between the NOT EXISTS check and the insert.
            // (A cosmically improbable view_secret collision also lands here and surfaces
            // as created=false -> NotFound upstream; acceptable.)
            return false;
        }
    }

    private static Timestamp toTs(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
