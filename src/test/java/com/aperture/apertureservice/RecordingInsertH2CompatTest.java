package com.aperture.apertureservice;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")   // boots on H2 in PostgreSQL mode — NO Docker involved
class RecordingInsertH2CompatTest {

    @Autowired Recordings recordings;
    @Autowired Users users;

    @Test
    void insertIfAbsentWorksOnDevH2() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("h2-" + UUID.randomUUID() + "@example.com"),
                "H2", new HashedPassword("h"), true, Instant.now());
        users.save(u);
        UUID recId = UuidCreator.getTimeOrderedEpoch();
        Recording r = new Recording(recId, u.id(), RecordingStatus.PENDING, Instant.now(), null,
                "apv_h2_" + UUID.randomUUID(), null, null);
        assertThat(recordings.insertIfAbsent(r)).isTrue();
        assertThat(recordings.insertIfAbsent(r)).isFalse();
        assertThat(recordings.byId(recId)).isPresent();
    }
}
