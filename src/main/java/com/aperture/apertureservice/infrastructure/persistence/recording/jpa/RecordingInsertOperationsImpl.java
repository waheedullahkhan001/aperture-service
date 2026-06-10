package com.aperture.apertureservice.infrastructure.persistence.recording.jpa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Uses native SQL with ON CONFLICT DO NOTHING to perform a race-safe insert.
 * This avoids poisoning the JPA EntityManager / Hibernate session with a
 * PersistenceException on constraint violation, which would mark the transaction
 * rollback-only even when the exception is caught.
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
        int rows = jdbc.sql("""
                INSERT INTO recordings
                    (id, user_id, status, started_at, ended_at, view_secret,
                     countdown_ends_at, alerts_dispatched_at)
                VALUES
                    (:id, :userId, :status, :startedAt, :endedAt, :viewSecret,
                     :countdownEndsAt, :alertsDispatchedAt)
                ON CONFLICT (id) DO NOTHING
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
    }

    private static Timestamp toTs(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
