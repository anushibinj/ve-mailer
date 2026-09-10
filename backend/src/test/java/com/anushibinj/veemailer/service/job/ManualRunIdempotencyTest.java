package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.JobTriggerType;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.NotificationService;
import com.anushibinj.veemailer.service.OctaneCacheService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The on-demand "Run" action (PollingService.runNow) creates a MANUAL ScheduledJobRun with
 * maxAttempts=1 and executes it through the same ScheduledJobExecutor pipeline as a scheduled
 * job — so it gets the same job_key creation gate, dispatch gate, and minimum-dispatch-gap
 * suppression checks A/B "for free". This covers the two guarantees the design calls for: a
 * double-click (or two admins clicking at once) cannot double-send, and a failed manual run
 * never lingers waiting for a retry.
 */
@ExtendWith(MockitoExtension.class)
class ManualRunIdempotencyTest {

    @Mock
    private ScheduledJobRunService scheduledJobRunService;

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private MailAuditLogRepository mailAuditLogRepository;

    @Mock
    private FilterService filterService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private OctaneCacheService octaneCacheService;

    @Mock
    private TransientFailureClassifier classifier;

    private Clock clock;
    private ScheduledJobExecutor executor;

    private final Instant fixedNow = Instant.parse("2026-01-01T09:00:05Z");
    private Workspace workspace;
    private Filter filter;
    private EmailSubscriber subscriber;
    private UUID jobRunId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        executor = new ScheduledJobExecutor(scheduledJobRunService, emailSubscriberRepository, mailAuditLogRepository,
                filterService, notificationService, octaneCacheService, classifier, clock);
        ReflectionTestUtils.setField(executor, "retryIntervalMinutes", 15);
        ReflectionTestUtils.setField(executor, "minDispatchGapMinutes", 30);

        workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        workspace.setStatus(WorkspaceStatus.ENABLED);

        filter = new Filter();
        filter.setId(UUID.randomUUID());
        filter.setTitle("Open Defects");
        filter.setWorkspace(workspace);

        subscriber = new EmailSubscriber();
        subscriber.setId(UUID.randomUUID());
        subscriber.setWorkspace(workspace);
        subscriber.setFilter(filter);
        subscriber.setRecipientEmail("a@b.com");
        subscriber.setStatus(Status.ACTIVE);

        jobRunId = UUID.randomUUID();
    }

    private ScheduledJobRun buildManualRun() {
        return ScheduledJobRun.builder()
                .id(jobRunId)
                .workspaceId(workspace.getId())
                .filterId(filter.getId())
                .subscriptionId(subscriber.getId())
                .subscriberIds(List.of(subscriber.getId()))
                .triggerType(JobTriggerType.MANUAL)
                .attemptCount(1)
                .maxAttempts(1)
                .status(JobRunStatus.RUNNING)
                .build();
    }

    @Test
    void manualRun_transientFetchFailure_failsImmediatelyWithoutSchedulingRetry() {
        ScheduledJobRun run = buildManualRun();
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        RuntimeException failure = new RuntimeException("connection reset");
        when(filterService.getFilterFields(filter.getId())).thenThrow(failure);
        when(classifier.isTransient(failure)).thenReturn(true);

        executor.execute(jobRunId);

        // attemptCount (1) is not < maxAttempts (1) — no retry, even though the failure is transient.
        verify(scheduledJobRunService, never()).markAwaitingRetry(any(), any(), any());
        verify(scheduledJobRunService).markFailed(run, "Failed after 1 attempts: connection reset");
        verify(notificationService).recordFailureForAll(jobRunId, List.of(subscriber), workspace, "Open Defects",
                "Failed after 1 attempts: connection reset");
    }

    @Test
    void secondManualRunMomentsLater_suppressedByRecencyCheckB_noDoubleSend() {
        // First manual run already succeeded and wrote a SUCCESS row a few seconds ago.
        ScheduledJobRun secondRun = buildManualRun();
        UUID secondJobRunId = UUID.randomUUID();
        secondRun.setId(secondJobRunId);
        when(scheduledJobRunService.findById(secondJobRunId)).thenReturn(Optional.of(secondRun));
        when(scheduledJobRunService.claim(secondJobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("name", "Item 1")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(entity));
        when(filterService.getQueryLimit()).thenReturn(25);
        when(mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                eq(subscriber.getId()), eq(DeliveryStatus.SUCCESS), any())).thenReturn(true);
        when(mailAuditLogRepository.findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(
                subscriber.getId(), DeliveryStatus.SUCCESS))
                .thenReturn(Optional.of(MailAuditLog.builder().sentAt(fixedNow.minusSeconds(5)).build()));

        executor.execute(secondJobRunId);

        verify(scheduledJobRunService).markSuppressed(secondRun);
        verify(scheduledJobRunService, never()).beginDispatch(any());
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentDoubleClick_secondClaimLoses_fetchOnlyRunsOnce() {
        // Two admins click "Run now" at the same instant — same job_key means only one run row
        // exists; the second executor invocation for the same runId loses the claim gate, so the
        // fetch (and therefore the send) only ever happens for the winner.
        ScheduledJobRun run = buildManualRun();
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true, false);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("name", "Item 1")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(entity));
        when(filterService.getQueryLimit()).thenReturn(25);
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        when(notificationService.prepare(any(), any(), anyInt(), any(), any()))
                .thenReturn(new NotificationService.PreparedDigest("<html/>", "Subject", 1));

        executor.execute(jobRunId); // first caller wins the claim
        executor.execute(jobRunId); // second caller loses it

        verify(scheduledJobRunService, times(2)).claim(jobRunId);
        verify(filterService, times(1)).getFilterFields(filter.getId());
        verify(notificationService, times(1)).dispatch(any(), any(), any(), any(), any());
    }
}
