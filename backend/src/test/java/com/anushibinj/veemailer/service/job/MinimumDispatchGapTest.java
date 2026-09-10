package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.NotificationService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the minimum-dispatch-gap collision scenario from the design doc: a late retry of one
 * job's digest landing minutes before the next scheduled tick, and the two checks that stop it
 * from becoming a duplicate send — in-flight check A (job level) and recency check B
 * (subscription level).
 */
@ExtendWith(MockitoExtension.class)
class MinimumDispatchGapTest {

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
    private TransientFailureClassifier classifier;

    private Clock clock;
    private ScheduledJobExecutor executor;

    /** The 10:00 tick. */
    private final Instant fixedNow = Instant.parse("2026-01-01T10:00:00Z");
    private Workspace workspace;
    private Filter filter;
    private EmailSubscriber subscriber;
    private UUID jobRunId;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        executor = new ScheduledJobExecutor(scheduledJobRunService, emailSubscriberRepository, mailAuditLogRepository,
                filterService, notificationService, classifier, clock);
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

    private ScheduledJobRun buildRun(List<UUID> subscriberIds) {
        return ScheduledJobRun.builder()
                .id(jobRunId)
                .workspaceId(workspace.getId())
                .filterId(filter.getId())
                .subscriberIds(subscriberIds)
                .attemptCount(1)
                .maxAttempts(4)
                .status(JobRunStatus.RUNNING)
                .build();
    }

    private void stubSuccessfulFetch() {
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("name", "Item 1")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(entity));
        when(filterService.getQueryLimit()).thenReturn(25);
    }

    // ── Check B: recency ─────────────────────────────────────────────────────

    @Test
    void lateRetrySuccessOneMinuteAgo_suppressesTheNextTickEntirely() {
        ScheduledJobRun run = buildRun(List.of(subscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        stubSuccessfulFetch();
        // The 09:00 run's retry delivered at 09:59 — one minute before this 10:00 tick.
        Instant lastSuccess = fixedNow.minusSeconds(60);
        when(mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                eq(subscriber.getId()), eq(DeliveryStatus.SUCCESS), any())).thenReturn(true);
        when(mailAuditLogRepository.findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(
                subscriber.getId(), DeliveryStatus.SUCCESS))
                .thenReturn(Optional.of(MailAuditLog.builder().sentAt(lastSuccess).build()));

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markSuppressed(run);
        verify(scheduledJobRunService, never()).beginDispatch(any());
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
        verify(notificationService).recordSuppressed(eq(jobRunId), eq(List.of(subscriber)), eq(workspace), eq("Open Defects"),
                eq("Suppressed: a digest for this subscription was delivered 1 minutes ago."));
    }

    @Test
    void successThirtyNineMinutesAgo_isOutsideTheGap_dispatchesNormally() {
        ScheduledJobRun run = buildRun(List.of(subscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        stubSuccessfulFetch();
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        // 39 minutes ago is older than the 30-minute gap, so the recency check does not trip.
        when(mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                eq(subscriber.getId()), eq(DeliveryStatus.SUCCESS), any())).thenReturn(false);
        NotificationService.PreparedDigest digest = new NotificationService.PreparedDigest("<html/>", "Subject", 1);
        when(notificationService.prepare(any(), any(), anyInt(), any(), any())).thenReturn(digest);

        executor.execute(jobRunId);

        verify(notificationService).dispatch(jobRunId, List.of(subscriber), digest, workspace, "Open Defects");
        verify(scheduledJobRunService).markSucceeded(run);
        verify(scheduledJobRunService, never()).markSuppressed(any());
        verify(mailAuditLogRepository, never()).findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(any(), any());
    }

    @Test
    void oneSubscriptionRecentlyDeliveredOneNot_onlyTheNewSubscriptionIsMailed() {
        EmailSubscriber freshSubscriber = new EmailSubscriber();
        freshSubscriber.setId(UUID.randomUUID());
        freshSubscriber.setWorkspace(workspace);
        freshSubscriber.setFilter(filter);
        freshSubscriber.setRecipientEmail("new@b.com");
        freshSubscriber.setStatus(Status.ACTIVE);

        ScheduledJobRun run = buildRun(List.of(subscriber.getId(), freshSubscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId(), freshSubscriber.getId())))
                .thenReturn(List.of(subscriber, freshSubscriber));
        stubSuccessfulFetch();
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        when(mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                eq(subscriber.getId()), eq(DeliveryStatus.SUCCESS), any())).thenReturn(true);
        when(mailAuditLogRepository.findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(
                subscriber.getId(), DeliveryStatus.SUCCESS))
                .thenReturn(Optional.of(MailAuditLog.builder().sentAt(fixedNow.minusSeconds(300)).build()));
        when(mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                eq(freshSubscriber.getId()), eq(DeliveryStatus.SUCCESS), any())).thenReturn(false);
        NotificationService.PreparedDigest digest = new NotificationService.PreparedDigest("<html/>", "Subject", 1);
        when(notificationService.prepare(any(), any(), anyInt(), any(), any())).thenReturn(digest);

        executor.execute(jobRunId);

        ArgumentCaptor<List<EmailSubscriber>> dispatchCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).dispatch(eq(jobRunId), dispatchCaptor.capture(), eq(digest), eq(workspace), eq("Open Defects"));
        assertThat(dispatchCaptor.getValue()).containsExactly(freshSubscriber);
        verify(notificationService).recordSuppressed(eq(jobRunId), eq(List.of(subscriber)), eq(workspace), eq("Open Defects"), any());
        verify(scheduledJobRunService).markSucceeded(run);
    }

    // ── Check A: in-flight ───────────────────────────────────────────────────

    @Test
    void anotherRunActivelyDispatchingWithFreshClaim_blocksCheckA() {
        ScheduledJobRun run = buildRun(List.of(subscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        stubSuccessfulFetch();
        when(scheduledJobRunService.hasOtherActiveRun(workspace.getId(), filter.getId(), jobRunId)).thenReturn(true);

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markSuppressed(run);
        verify(scheduledJobRunService, never()).beginDispatch(any());
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
        // Check A trips before check B is ever consulted.
        verifyNoInteractions(mailAuditLogRepository);
    }

    @Test
    void anotherRunWithStaleClaim_doesNotBlockCheckA() {
        ScheduledJobRun run = buildRun(List.of(subscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        stubSuccessfulFetch();
        when(scheduledJobRunService.hasOtherActiveRun(workspace.getId(), filter.getId(), jobRunId)).thenReturn(false);
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        NotificationService.PreparedDigest digest = new NotificationService.PreparedDigest("<html/>", "Subject", 1);
        when(notificationService.prepare(any(), any(), anyInt(), any(), any())).thenReturn(digest);

        executor.execute(jobRunId);

        verify(notificationService).dispatch(jobRunId, List.of(subscriber), digest, workspace, "Open Defects");
        verify(scheduledJobRunService).markSucceeded(run);
    }

    // ── Disabling suppression ────────────────────────────────────────────────

    @Test
    void minDispatchGapMinutesZero_disablesSuppressionEntirely() {
        ReflectionTestUtils.setField(executor, "minDispatchGapMinutes", 0);
        ScheduledJobRun run = buildRun(List.of(subscriber.getId()));
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
        stubSuccessfulFetch();
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        NotificationService.PreparedDigest digest = new NotificationService.PreparedDigest("<html/>", "Subject", 1);
        when(notificationService.prepare(any(), any(), anyInt(), any(), any())).thenReturn(digest);

        executor.execute(jobRunId);

        verify(notificationService).dispatch(jobRunId, List.of(subscriber), digest, workspace, "Open Defects");
        verifyNoInteractions(mailAuditLogRepository);
    }
}
