package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.model.DeliveryStatus;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledJobExecutorTest {

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
    private com.anushibinj.veemailer.service.OctaneCacheService octaneCacheService;

    @Mock
    private TransientFailureClassifier classifier;

    private Clock clock;
    private ScheduledJobExecutor executor;

    private final Instant fixedNow = Instant.parse("2026-01-01T09:00:00Z");
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

    private ScheduledJobRun buildRun(int attemptCount, int maxAttempts) {
        return ScheduledJobRun.builder()
                .id(jobRunId)
                .workspaceId(workspace.getId())
                .filterId(filter.getId())
                .subscriberIds(List.of(subscriber.getId()))
                .attemptCount(attemptCount)
                .maxAttempts(maxAttempts)
                .status(JobRunStatus.RUNNING)
                .build();
    }

    private void stubClaimSucceeds(ScheduledJobRun run) {
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));
    }

    @Test
    void execute_runNotFound_doesNothing() {
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.empty());

        executor.execute(jobRunId);

        verifyNoInteractions(filterService, notificationService);
    }

    @Test
    void execute_claimLost_doesNothing() {
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(buildRun(0, 4)));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(false);

        executor.execute(jobRunId);

        verifyNoInteractions(filterService, notificationService, emailSubscriberRepository);
    }

    @Test
    void execute_transientFetchFailure_marksAwaitingRetryAndNeverSends() {
        ScheduledJobRun run = buildRun(1, 4);
        stubClaimSucceeds(run);
        RuntimeException failure = new RuntimeException("connection reset");
        when(filterService.getFilterFields(filter.getId())).thenThrow(failure);
        when(classifier.isTransient(failure)).thenReturn(true);

        executor.execute(jobRunId);

        ArgumentCaptor<Instant> nextRetryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(scheduledJobRunService).markAwaitingRetry(eq(run), eq("connection reset"), nextRetryCaptor.capture());
        assertThat(nextRetryCaptor.getValue()).isEqualTo(fixedNow.plusSeconds(15 * 60));
        verify(notificationService).recordRetryingForAll(jobRunId, List.of(subscriber), workspace, "Open Defects", 1, 15);
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
        verify(scheduledJobRunService, never()).beginDispatch(any());
    }

    @Test
    void execute_permanentFailure_failsOnFirstAttempt() {
        ScheduledJobRun run = buildRun(1, 4);
        stubClaimSucceeds(run);
        RuntimeException failure = new IllegalArgumentException("Filter not found");
        when(filterService.getFilterFields(filter.getId())).thenThrow(failure);
        when(classifier.isTransient(failure)).thenReturn(false);

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markFailed(run, "Filter not found");
        verify(notificationService).recordFailureForAll(jobRunId, List.of(subscriber), workspace, "Open Defects", "Filter not found");
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
        verify(scheduledJobRunService, never()).markAwaitingRetry(any(), any(), any());
    }

    @Test
    void execute_attempt4Of4TransientFailure_fails() {
        ScheduledJobRun run = buildRun(4, 4);
        stubClaimSucceeds(run);
        RuntimeException failure = new RuntimeException("connection reset");
        when(filterService.getFilterFields(filter.getId())).thenThrow(failure);
        when(classifier.isTransient(failure)).thenReturn(true);

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markFailed(run, "Failed after 4 attempts: connection reset");
        verify(notificationService).recordFailureForAll(jobRunId, List.of(subscriber), workspace, "Open Defects",
                "Failed after 4 attempts: connection reset");
        verify(scheduledJobRunService, never()).markAwaitingRetry(any(), any(), any());
    }

    @Test
    void execute_successOnFetch_dispatchesAndMarksSucceeded() {
        ScheduledJobRun run = buildRun(3, 4);
        stubClaimSucceeds(run);
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("name", "Item 1")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(entity));
        when(filterService.getQueryLimit()).thenReturn(25);
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(true);
        NotificationService.PreparedDigest digest = new NotificationService.PreparedDigest("<html/>", "Subject", 1);
        when(notificationService.prepare(List.of(entity), List.of("name"), 25, workspace, "Open Defects")).thenReturn(digest);

        executor.execute(jobRunId);

        verify(notificationService).dispatch(jobRunId, List.of(subscriber), digest, workspace, "Open Defects");
        verify(scheduledJobRunService).markSucceeded(run);
        verify(scheduledJobRunService, never()).markAwaitingRetry(any(), any(), any());
        verify(scheduledJobRunService, never()).markFailed(any(), any());
    }

    @Test
    void execute_emptyResults_recordsSkippedAndSucceedsWithoutDispatchGate() {
        ScheduledJobRun run = buildRun(1, 4);
        stubClaimSucceeds(run);
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(Collections.emptyList());
        when(filterService.getQueryLimit()).thenReturn(25);

        executor.execute(jobRunId);

        verify(notificationService).recordSkippedNoTickets(jobRunId, List.of(subscriber), workspace, "Open Defects");
        verify(scheduledJobRunService).markSucceeded(run);
        verify(scheduledJobRunService, never()).beginDispatch(any());
        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
    }

    @Test
    void execute_dispatchGateLost_abortsWithoutSending() {
        ScheduledJobRun run = buildRun(1, 4);
        stubClaimSucceeds(run);
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("name", "Item 1")));
        when(filterService.getFilterFields(filter.getId())).thenReturn(List.of("name"));
        when(filterService.executeFilter(filter.getId(), workspace.getId())).thenReturn(List.of(entity));
        when(filterService.getQueryLimit()).thenReturn(25);
        when(scheduledJobRunService.beginDispatch(jobRunId)).thenReturn(false);

        executor.execute(jobRunId);

        verify(notificationService, never()).dispatch(any(), any(), any(), any(), any());
        verify(notificationService, never()).prepare(any(), any(), anyInt(), any(), any());
        verify(scheduledJobRunService, never()).markSucceeded(any());
    }

    @Test
    void execute_noLiveSubscribersLeft_marksSucceededWithoutFetching() {
        ScheduledJobRun run = buildRun(1, 4);
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of());

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markSucceeded(run);
        verifyNoInteractions(filterService, notificationService);
    }

    @Test
    void execute_disabledSubscriberFiltered_notReloadedIntoTheRun() {
        ScheduledJobRun run = buildRun(1, 4);
        subscriber.setStatus(Status.DISABLED);
        when(scheduledJobRunService.findById(jobRunId)).thenReturn(Optional.of(run));
        when(scheduledJobRunService.claim(jobRunId)).thenReturn(true);
        when(emailSubscriberRepository.findAllById(List.of(subscriber.getId()))).thenReturn(List.of(subscriber));

        executor.execute(jobRunId);

        verify(scheduledJobRunService).markSucceeded(run);
        verifyNoInteractions(filterService);
    }
}
