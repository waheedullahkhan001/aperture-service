package com.aperture.apertureservice.infrastructure.scheduled;

import com.aperture.apertureservice.domain.emergency.api.RetryFailedAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AlertRetrySweeper {

    private static final Logger log = LoggerFactory.getLogger(AlertRetrySweeper.class);

    private final RetryFailedAlerts retryFailedAlerts;
    private final TransactionTemplate tx;

    public AlertRetrySweeper(RetryFailedAlerts retryFailedAlerts, TransactionTemplate tx) {
        this.retryFailedAlerts = retryFailedAlerts;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.schedule.retry-delay}")
    public void tick() {
        try {
            tx.executeWithoutResult(status -> retryFailedAlerts.retry());
        } catch (Exception e) {
            log.error("Alert retry sweep failed", e);
        }
    }
}
