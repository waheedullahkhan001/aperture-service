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
        // Each thread mints its own distinct view_secret, mirroring production where every
        // ensure() call generates a fresh random secret. INSERT ... ON CONFLICT (id) DO NOTHING
        // only suppresses the id conflict; a shared secret would also trip the UNIQUE
        // view_secret index, causing a spurious DuplicateKeyException on the losing thread.
        Callable<Boolean> attempt = () -> {
            start.await();
            Recording candidate = new Recording(recId, u.id(), RecordingStatus.PENDING, Instant.now(), null,
                    "apv_race_" + UUID.randomUUID(), null, null);  // distinct secret per thread, same id
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
