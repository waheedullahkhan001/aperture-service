package com.aperture.apertureservice.infrastructure.scheduled;

import com.aperture.apertureservice.domain.emergency.api.DispatchAlerts;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Component
public class AlertDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatchScheduler.class);

    private final Recordings recordings;
    private final DispatchAlerts dispatchAlerts;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AlertDispatchScheduler(Recordings recordings, DispatchAlerts dispatchAlerts,
                                  TransactionTemplate tx, Clock clock) {
        this.recordings = recordings;
        this.dispatchAlerts = dispatchAlerts;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.schedule.dispatch-delay}")
    public void tick() {
        for (Recording due : recordings.dispatchDue(clock.instant())) {
            try {
                tx.executeWithoutResult(status -> dispatchAlerts.dispatch(due.id()));
            } catch (Exception e) {
                log.error("Alert dispatch failed for recording {}", due.id(), e);
            }
        }
    }
}
