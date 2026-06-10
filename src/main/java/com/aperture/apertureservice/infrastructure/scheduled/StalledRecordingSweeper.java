package com.aperture.apertureservice.infrastructure.scheduled;

import com.aperture.apertureservice.domain.recording.api.MarkStalledRecordings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StalledRecordingSweeper {

    private static final Logger log = LoggerFactory.getLogger(StalledRecordingSweeper.class);

    private final MarkStalledRecordings markStalled;
    private final TransactionTemplate tx;

    public StalledRecordingSweeper(MarkStalledRecordings markStalled, TransactionTemplate tx) {
        this.markStalled = markStalled;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.schedule.sweep-delay}")
    public void tick() {
        try {
            Integer swept = tx.execute(status -> markStalled.sweep());
            if (swept != null && swept > 0) {
                log.info("Marked {} stalled recordings as failed", swept);
            }
        } catch (Exception e) {
            log.error("Stalled recording sweep failed", e);
        }
    }
}
