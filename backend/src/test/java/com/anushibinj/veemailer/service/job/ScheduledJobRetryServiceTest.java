package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * The exclusivity guarantee itself (two concurrent claims yielding exactly one winner) is a
 * property of {@link ScheduledJobRunService#claim}'s conditional DB update, covered in
 * {@code ScheduledJobRunServiceTest}. This class covers the retry driver's own responsibilities:
 * it only hands the executor runs the repository has already decided are due, and one run's
 * failure doesn't stop the rest of the tick.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledJobRetryServiceTest {

    @Mock
    private ScheduledJobRunService scheduledJobRunService;

    @Mock
    private ScheduledJobExecutor scheduledJobExecutor;

    @InjectMocks
    private ScheduledJobRetryService retryService;

    @Test
    void processDueRetries_sweepsStaleDispatchingBeforeProcessingRetries() {
        when(scheduledJobRunService.findDueForRetry()).thenReturn(List.of());

        retryService.processDueRetries();

        var inOrder = inOrder(scheduledJobRunService);
        inOrder.verify(scheduledJobRunService).sweepStaleDispatching();
        inOrder.verify(scheduledJobRunService).findDueForRetry();
    }

    @Test
    void processDueRetries_onlyExecutesRunsTheRepositoryReturnedAsDue() {
        ScheduledJobRun due1 = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.AWAITING_RETRY).build();
        ScheduledJobRun due2 = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.AWAITING_RETRY).build();
        when(scheduledJobRunService.findDueForRetry()).thenReturn(List.of(due1, due2));

        retryService.processDueRetries();

        verify(scheduledJobExecutor).execute(due1.getId());
        verify(scheduledJobExecutor).execute(due2.getId());
        verifyNoMoreInteractions(scheduledJobExecutor);
    }

    @Test
    void processDueRetries_noRunsDue_executorNeverCalled() {
        when(scheduledJobRunService.findDueForRetry()).thenReturn(List.of());

        retryService.processDueRetries();

        verifyNoInteractions(scheduledJobExecutor);
    }

    @Test
    void processDueRetries_oneRunThrows_othersStillProcessed() {
        ScheduledJobRun failing = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.AWAITING_RETRY).build();
        ScheduledJobRun healthy = ScheduledJobRun.builder().id(UUID.randomUUID()).status(JobRunStatus.AWAITING_RETRY).build();
        when(scheduledJobRunService.findDueForRetry()).thenReturn(List.of(failing, healthy));
        doThrow(new RuntimeException("boom")).when(scheduledJobExecutor).execute(failing.getId());

        retryService.processDueRetries();

        verify(scheduledJobExecutor).execute(failing.getId());
        verify(scheduledJobExecutor).execute(healthy.getId());
    }
}
