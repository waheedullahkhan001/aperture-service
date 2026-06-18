package com.aperture.apertureservice.infrastructure.persistence.account;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.infrastructure.persistence.account.jpa.JpaUsers;
import com.aperture.apertureservice.support.JpaSliceTest;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JpaSliceTest
@Import(JpaUsers.class)
class JpaUsersTest {

    @Autowired
    JpaUsers users;

    private User user(String email) {
        return new User(UuidCreator.getTimeOrderedEpoch(), new Email(email), "Name",
                new HashedPassword("$2a$12$hash"), false, Instant.parse("2026-06-07T12:00:00Z"));
    }

    @Test
    void savesAndFindsByIdAndEmailRoundTrip() {
        User u = user("a@example.com");
        users.save(u);
        assertThat(users.byId(u.id())).contains(u);
        assertThat(users.byEmail(new Email("A@EXAMPLE.COM"))).contains(u);
    }

    @Test
    void updateByResave() {
        User u = user("a@example.com");
        users.save(u);
        users.save(u.verifiedNow());
        assertThat(users.byId(u.id()).orElseThrow().verified()).isTrue();
    }

    @Test
    void deleteRemoves() {
        User u = user("a@example.com");
        users.save(u);
        users.delete(u.id());
        assertThat(users.byId(u.id())).isEmpty();
    }
}
