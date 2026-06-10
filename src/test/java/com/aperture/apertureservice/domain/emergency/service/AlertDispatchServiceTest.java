package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.account.HashedPassword;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.stubs.CapturingEmailSender;
import com.aperture.apertureservice.domain.account.spi.stubs.InMemoryUsers;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.spi.stubs.InMemoryAlertConfigurations;
import com.aperture.apertureservice.domain.emergency.spi.stubs.InMemoryAlertDispatchAttempts;
import com.aperture.apertureservice.domain.emergency.spi.stubs.InMemoryEmergencyContacts;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.spi.stubs.InMemoryRecordings;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDispatchServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-07T12:00:00Z");

    private final InMemoryRecordings recordings = new InMemoryRecordings();
    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryEmergencyContacts contacts = new InMemoryEmergencyContacts();
    private final InMemoryAlertConfigurations configs = new InMemoryAlertConfigurations();
    private final InMemoryAlertDispatchAttempts attempts = new InMemoryAlertDispatchAttempts();
    private final CapturingEmailSender emails = new CapturingEmailSender();
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private final AlertDispatchService service = new AlertDispatchService(recordings, users, contacts,
            configs, attempts, emails, clock, "http://localhost");

    private UUID userId;
    private UUID recId;
    private String viewSecret;

    @BeforeEach
    void seed() {
        User u = new User(UuidCreator.getTimeOrderedEpoch(), new Email("owner@example.com"), "Olivia Owner",
                new HashedPassword("h"), true, T0);
        users.save(u);
        userId = u.id();
        Recording r = new Recording(UuidCreator.getTimeOrderedEpoch(), userId, RecordingStatus.RECORDING,
                T0, null, "apv_secret", T0.minusSeconds(1), null);
        recordings.save(r);
        recId = r.id();
        viewSecret = r.viewSecret();
    }

    @Test
    void dispatchSendsRenderedTemplateToEveryContactAndMarksDispatched() {
        contacts.save(new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        contacts.save(new EmergencyContact(null, userId, "Dad", new Email("dad@example.com"),
                "Custom for {{ownerName}}"));
        configs.save(new AlertConfiguration(userId, 30, "{{ownerName}} needs help: {{streamUrl}}"));

        service.dispatch(recId);

        String watchUrl = "http://localhost/watch/" + recId + "?t=" + viewSecret;
        assertThat(emails.to("mom@example.com").get(0).body())
                .isEqualTo("Olivia Owner needs help: " + watchUrl);
        assertThat(emails.to("mom@example.com").get(0).subject())
                .isEqualTo("Emergency alert from Olivia Owner");
        // per-contact override used, and the stream link appended because the override lacks it
        assertThat(emails.to("dad@example.com").get(0).body())
                .startsWith("Custom for Olivia Owner")
                .contains(watchUrl);
        assertThat(recordings.byId(recId).orElseThrow().alertsDispatchedAt()).isEqualTo(T0);
        assertThat(attempts.all()).hasSize(2).allMatch(AlertDispatchAttempt::success);
    }

    @Test
    void dispatchIsIdempotent() {
        contacts.save(new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        service.dispatch(recId);
        service.dispatch(recId);
        assertThat(emails.to("mom@example.com")).hasSize(1);
    }

    @Test
    void dispatchSkipsEndedOrUnknownOrContactlessRecordings() {
        service.dispatch(UUID.randomUUID());                       // unknown: no-op
        service.dispatch(recId);                                   // no contacts: no-op
        assertThat(emails.all()).isEmpty();
        assertThat(recordings.byId(recId).orElseThrow().alertsDispatchedAt()).isNull();

        contacts.save(new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        recordings.save(recordings.byId(recId).orElseThrow().ended(T0));
        service.dispatch(recId);                                   // ended: no-op
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void failingSendIsRetriedOnceInlineAndEveryAttemptRecorded() {
        contacts.save(new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        FlakyEmailSender flaky = new FlakyEmailSender(1); // first call fails, second succeeds
        AlertDispatchService flakyService = new AlertDispatchService(recordings, users, contacts,
                configs, attempts, flaky, clock, "http://localhost");

        flakyService.dispatch(recId);

        assertThat(attempts.all()).hasSize(2);
        assertThat(attempts.all().get(0).success()).isFalse();
        assertThat(attempts.all().get(0).errorMessage()).isNotBlank();
        assertThat(attempts.all().get(1).success()).isTrue();
        assertThat(recordings.byId(recId).orElseThrow().alertsDispatchedAt()).isEqualTo(T0);
    }

    @Test
    void retryResendsRecentFailuresUpToThreeTotalAttempts() {
        EmergencyContact mom = contacts.save(
                new EmergencyContact(null, userId, "Mom", new Email("mom@example.com"), null));
        // two prior failures within the window, none successful
        attempts.record(new AlertDispatchAttempt(null, recId, mom.id(), T0.minusSeconds(60), false, "x"));
        attempts.record(new AlertDispatchAttempt(null, recId, mom.id(), T0.minusSeconds(30), false, "x"));

        int retried = service.retry();
        assertThat(retried).isEqualTo(1);
        assertThat(emails.to("mom@example.com")).hasSize(1);

        // now 3 total attempts -> no further retries
        assertThat(service.retry()).isZero();
    }

    /** Fails the first N sends, then succeeds. */
    static class FlakyEmailSender implements EmailSender {
        private final CapturingEmailSender delegate = new CapturingEmailSender();
        private int failuresLeft;
        FlakyEmailSender(int failures) { this.failuresLeft = failures; }
        @Override public void send(String to, String subject, String body) {
            if (failuresLeft-- > 0) throw new IllegalStateException("smtp down");
            delegate.send(to, subject, body);
        }
    }
}
