package com.aperture.apertureservice.infrastructure.persistence.recording;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.aperture.apertureservice.support.IntegrationTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RecordingInsertRaceTest {

    @Autowired
    Recordings recordings;

    @Autowired
    Users users;

    @Test
    void concurrentInsertIfAbsentExactlyOneWins() throws Exception {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("race-" + UUID.randomUUID() + "@example.com"), "R",
                new HashedPassword("h"), true, Instant.now());
        users.save(u);
        UUID recId = UuidCreator.getTimeOrderedEpoch();

        CountDownLatch start = new CountDownLatch(1);
        // Build a fresh Recording instance per thread from the same values to avoid
        // sharing a single entity that Hibernate might mutate on persist.
        Callable<Boolean> attempt = () -> {
            start.await();
            Recording candidate = new Recording(recId, u.id(), RecordingStatus.PENDING, Instant.now(), null,
                    "apv_race_" + recId, null, null);
            return recordings.insertIfAbsent(candidate);
        };
        try {
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<Boolean> a = pool.submit(attempt);
                Future<Boolean> b = pool.submit(attempt);
                start.countDown();
                assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
            }
            assertThat(recordings.byId(recId)).isPresent();
        } finally {
            recordings.delete(recId);
            users.delete(u.id());
        }
    }
}
