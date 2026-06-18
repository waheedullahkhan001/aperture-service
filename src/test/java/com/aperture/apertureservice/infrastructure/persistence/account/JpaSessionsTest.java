package com.aperture.apertureservice.infrastructure.persistence.account;

import com.aperture.apertureservice.domain.account.Session;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.JpaSessions;
import com.aperture.apertureservice.support.JpaSliceTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JpaSliceTest
@Import(JpaSessions.class)
class JpaSessionsTest {

    @Autowired
    JpaSessions sessions;

    @Autowired
    TestEntityManager em;

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

    @Test
    void findsByRefreshAndPreviousHash() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        Session s = new Session(UuidCreator.getTimeOrderedEpoch(), userId, "Firefox", "#new", "#old",
                t, t, t.plusSeconds(3600));
        sessions.save(s);

        assertThat(sessions.byRefreshTokenHash("#new")).contains(s);
        assertThat(sessions.byPreviousTokenHash("#old")).contains(s);
        assertThat(sessions.byUser(userId)).containsExactly(s);
        assertThat(sessions.byId(s.id())).contains(s);
    }

    @Test
    void deleteAllForUserRemovesEverySession() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        sessions.save(new Session(UuidCreator.getTimeOrderedEpoch(), userId, "a", "#1", null, t, t, t));
        sessions.save(new Session(UuidCreator.getTimeOrderedEpoch(), userId, "b", "#2", null, t, t, t));
        sessions.deleteAllForUser(userId);
        assertThat(sessions.byUser(userId)).isEmpty();
    }

    @Test
    void deleteRemovesSingleSession() {
        UUID userId = seedUser();
        Instant t = Instant.parse("2026-06-07T12:00:00Z");
        Session s = new Session(UuidCreator.getTimeOrderedEpoch(), userId, "a", "#1", null, t, t, t);
        sessions.save(s);
        sessions.delete(s.id());
        assertThat(sessions.byId(s.id())).isEmpty();
    }
}
