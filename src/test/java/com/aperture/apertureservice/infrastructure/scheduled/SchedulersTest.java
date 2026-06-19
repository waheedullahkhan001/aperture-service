package com.aperture.apertureservice.infrastructure.scheduled;

import com.aperture.apertureservice.domain.emergency.api.DispatchAlerts;
import com.aperture.apertureservice.domain.emergency.api.RetryFailedAlerts;
import com.aperture.apertureservice.domain.recording.Recording;
import com.aperture.apertureservice.domain.recording.RecordingStatus;
import com.aperture.apertureservice.domain.recording.api.MarkStalledRecordings;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.support.TransactionCallback;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulersTest {

    private final Recordings recordings = mock(Recordings.class);
    private final DispatchAlerts dispatchAlerts = mock(DispatchAlerts.class);
    private final RetryFailedAlerts retryFailedAlerts = mock(RetryFailedAlerts.class);
    private final MarkStalledRecordings markStalled = mock(MarkStalledRecordings.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-07T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void passThroughTransactions() {
        doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        doAnswer(inv -> inv.<TransactionCallback<Integer>>getArgument(0)
                .doInTransaction(mock(TransactionStatus.class)))
                .when(tx).execute(any());
    }

    @Test
    void dispatchTickProcessesEveryDueRecordingEvenWhenOneFails() {
        Recording a = new Recording(UUID.randomUUID(), UUID.randomUUID(), RecordingStatus.RECORDING,
                Instant.EPOCH, null, "apv_a", Instant.EPOCH, null, false);
        Recording b = new Recording(UUID.randomUUID(), UUID.randomUUID(), RecordingStatus.RECORDING,
                Instant.EPOCH, null, "apv_b", Instant.EPOCH, null, false);
        when(recordings.dispatchDue(clock.instant())).thenReturn(List.of(a, b));
        doThrow(new RuntimeException("boom")).when(dispatchAlerts).dispatch(a.id());

        new AlertDispatchScheduler(recordings, dispatchAlerts, tx, clock).tick();

        verify(dispatchAlerts).dispatch(a.id());
        verify(dispatchAlerts).dispatch(b.id());   // failure on A does not stop B
    }

    @Test
    void retrySweeperDelegatesAndSurvivesFailure() {
        when(retryFailedAlerts.retry()).thenReturn(2);
        new AlertRetrySweeper(retryFailedAlerts, tx).tick();
        verify(retryFailedAlerts).retry();

        doThrow(new RuntimeException("db down")).when(retryFailedAlerts).retry();
        new AlertRetrySweeper(retryFailedAlerts, tx).tick(); // must not propagate
    }

    @Test
    void stalledSweeperDelegatesAndSurvivesFailure() {
        when(markStalled.sweep()).thenReturn(1);
        new StalledRecordingSweeper(markStalled, tx).tick();
        verify(markStalled).sweep();

        doThrow(new RuntimeException("db down")).when(markStalled).sweep();
        new StalledRecordingSweeper(markStalled, tx).tick(); // must not propagate
    }
}
