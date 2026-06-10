package com.aperture.apertureservice.domain.emergency.service;

import com.aperture.apertureservice.ddd.DomainService;
import com.aperture.apertureservice.domain.account.User;
import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.AlertDispatchAttempt;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.DispatchAlerts;
import com.aperture.apertureservice.domain.emergency.api.RetryFailedAlerts;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;
import com.aperture.apertureservice.domain.emergency.spi.AlertDispatchAttempts;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.WatchUrls;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@DomainService
public class AlertDispatchService implements DispatchAlerts, RetryFailedAlerts {

    static final Duration RETRY_WINDOW = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS_PER_CONTACT = 3;

    private final Recordings recordings;
    private final Users users;
    private final EmergencyContacts contacts;
    private final AlertConfigurations configs;
    private final AlertDispatchAttempts attempts;
    private final EmailSender emails;
    private final Clock clock;
    private final String publicOrigin;

    public AlertDispatchService(Recordings recordings, Users users, EmergencyContacts contacts,
                                AlertConfigurations configs, AlertDispatchAttempts attempts,
                                EmailSender emails, Clock clock, String publicOrigin) {
        this.recordings = recordings;
        this.users = users;
        this.contacts = contacts;
        this.configs = configs;
        this.attempts = attempts;
        this.emails = emails;
        this.clock = clock;
        this.publicOrigin = publicOrigin;
    }

    @Override
    @Transactional
    public void dispatch(UUID recordingId) {
        Recording r = recordings.byIdForUpdate(recordingId).orElse(null);
        if (r == null || !r.live() || r.alertsDispatchedAt() != null) {
            return;
        }
        List<EmergencyContact> list = contacts.byUser(r.userId());
        if (list.isEmpty()) {
            return;
        }
        String ownerName = users.byId(r.userId()).map(User::fullname).orElse("An Aperture user");
        String watchUrl = WatchUrls.of(publicOrigin, r.id(), r.viewSecret());
        AlertConfiguration config = configs.byUser(r.userId()).orElse(AlertConfiguration.defaults(r.userId()));
        for (EmergencyContact contact : list) {
            String template = contact.messageOverride() != null ? contact.messageOverride()
                    : config.messageTemplate();
            String body = render(template, ownerName, watchUrl);
            sendWithOneInlineRetry(r.id(), contact, ownerName, body);
        }
        recordings.save(r.dispatched(clock.instant()));
    }

    @Override
    @Transactional
    public int retry() {
        Instant now = clock.instant();
        Set<String> seen = new HashSet<>();
        int retried = 0;
        for (AlertDispatchAttempt failed : attempts.failedSince(now.minus(RETRY_WINDOW))) {
            if (failed.recordingId() == null || failed.contactId() == null) continue;
            if (!seen.add(failed.recordingId() + "/" + failed.contactId())) continue;
            if (attempts.anySuccess(failed.recordingId(), failed.contactId())) continue;
            if (attempts.countFor(failed.recordingId(), failed.contactId()) >= MAX_ATTEMPTS_PER_CONTACT) continue;
            Recording r = recordings.byId(failed.recordingId()).orElse(null);
            if (r == null || !r.live()) continue;
            EmergencyContact contact = contacts.byId(failed.contactId()).orElse(null);
            if (contact == null) continue;
            String ownerName = users.byId(r.userId()).map(User::fullname).orElse("An Aperture user");
            String watchUrl = WatchUrls.of(publicOrigin, r.id(), r.viewSecret());
            AlertConfiguration config = configs.byUser(r.userId()).orElse(AlertConfiguration.defaults(r.userId()));
            String template = contact.messageOverride() != null ? contact.messageOverride()
                    : config.messageTemplate();
            sendOnce(r.id(), contact, ownerName, render(template, ownerName, watchUrl));
            retried++;
        }
        return retried;
    }

    private String render(String template, String ownerName, String watchUrl) {
        String body = template.replace("{{streamUrl}}", watchUrl).replace("{{ownerName}}", ownerName);
        if (!body.contains(watchUrl)) {
            body = body + "\n\nLive stream: " + watchUrl;
        }
        return body;
    }

    private void sendWithOneInlineRetry(UUID recordingId, EmergencyContact contact,
                                        String ownerName, String body) {
        if (!sendOnce(recordingId, contact, ownerName, body)) {
            sendOnce(recordingId, contact, ownerName, body);
        }
    }

    private boolean sendOnce(UUID recordingId, EmergencyContact contact, String ownerName, String body) {
        try {
            emails.send(contact.email().value(), "Emergency alert from " + ownerName, body);
            attempts.record(new AlertDispatchAttempt(null, recordingId, contact.id(),
                    clock.instant(), true, null));
            return true;
        } catch (Exception e) {
            attempts.record(new AlertDispatchAttempt(null, recordingId, contact.id(),
                    clock.instant(), false, e.toString()));
            return false;
        }
    }
}
