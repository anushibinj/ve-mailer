package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.service.job.ScheduledJobExecutor;
import com.anushibinj.veemailer.service.job.ScheduledJobRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollingServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private ScheduledJobRunService scheduledJobRunService;

    @Mock
    private ScheduledJobExecutor scheduledJobExecutor;

    private PollingService pollingService;

    private Workspace workspace1;
    private Workspace workspace2;
    private Filter filter1;
    private Filter filter2;
    private final Instant fixedNow = Instant.parse("2026-01-01T09:17:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        pollingService = new PollingService(emailSubscriberRepository, scheduledJobRunService, scheduledJobExecutor, clock);
        ReflectionTestUtils.setField(pollingService, "maxAttempts", 4);

        workspace1 = new Workspace();
        workspace1.setId(UUID.randomUUID());
        workspace1.setClientId("client-id-1");
        workspace1.setClientKey("client-key-1");
        workspace1.setSharedSpaceId("1001");
        workspace1.setWorkspaceId("2001");

        workspace2 = new Workspace();
        workspace2.setId(UUID.randomUUID());

        filter1 = new Filter();
        filter1.setId(UUID.randomUUID());
        filter1.setTitle("Open Items");
        filter1.setWorkspace(workspace1);

        filter2 = new Filter();
        filter2.setId(UUID.randomUUID());
        filter2.setWorkspace(workspace1);
    }

    private void stubRunCreation() {
        lenient().when(scheduledJobRunService.createScheduledRun(any(), any(), any(), anyList(), anyInt()))
                .thenAnswer(inv -> Optional.of(ScheduledJobRun.builder().id(UUID.randomUUID()).build()));
    }

    @Test
    void testProcessAtHour_Daily_GroupsCorrectly() {
        stubRunCreation();
        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setId(UUID.randomUUID());
        sub1.setWorkspace(workspace1);
        sub1.setFilter(filter1);

        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setId(UUID.randomUUID());
        sub2.setWorkspace(workspace1);
        sub2.setFilter(filter1); // Same group as sub1

        EmailSubscriber sub3 = new EmailSubscriber();
        sub3.setId(UUID.randomUUID());
        sub3.setWorkspace(workspace1);
        sub3.setFilter(filter2); // Different filter

        EmailSubscriber sub4 = new EmailSubscriber();
        sub4.setId(UUID.randomUUID());
        sub4.setWorkspace(workspace2);
        sub4.setFilter(filter1); // Different workspace

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Arrays.asList(sub1, sub2, sub3, sub4));

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        // Group 1: sub1, sub2  |  Group 2: sub3  |  Group 3: sub4
        verify(scheduledJobRunService, times(3)).createScheduledRun(any(), any(), any(), anyList(), eq(4));
        verify(scheduledJobExecutor, times(3)).execute(any());
    }

    /**
     * Core deduplication test: when two subscribers share the same workspace+filter and
     * the same scheduled hour, exactly ONE job run must be created covering both recipients.
     */
    @Test
    void testProcessAtHour_SharedFilter_CreatesOneJobRunWithBothSubscriberIds() {
        stubRunCreation();
        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setId(UUID.randomUUID());
        sub1.setWorkspace(workspace1);
        sub1.setFilter(filter1);
        sub1.setRecipientEmail("alice@example.com");

        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setId(UUID.randomUUID());
        sub2.setWorkspace(workspace1);
        sub2.setFilter(filter1); // Same workspace + filter → same group
        sub2.setRecipientEmail("bob@example.com");

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Arrays.asList(sub1, sub2));

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        // Exactly one job run created for the shared (workspace, filter) group
        verify(scheduledJobRunService, times(1)).createScheduledRun(
                eq(workspace1.getId()), eq(filter1.getId()), any(), anyList(), eq(4));

        ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(scheduledJobRunService).createScheduledRun(any(), any(), any(), idsCaptor.capture(), anyInt());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(sub1.getId(), sub2.getId());
    }

    @Test
    void testProcessAtHour_Empty_NoJobRunsCreated() {
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verifyNoInteractions(scheduledJobRunService, scheduledJobExecutor);
    }

    @Test
    void testProcessAtHour_Weekly_FiresOnlyOnMonday() {
        stubRunCreation();
        EmailSubscriber weeklySub = new EmailSubscriber();
        weeklySub.setId(UUID.randomUUID());
        weeklySub.setWorkspace(workspace1);
        weeklySub.setFilter(filter1);

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.WEEKLY, Status.ACTIVE))
                .thenReturn(List.of(weeklySub));

        pollingService.processAtHour(9, DayOfWeek.MONDAY);

        verify(scheduledJobRunService, times(1)).createScheduledRun(any(), any(), any(), anyList(), anyInt());
    }

    @Test
    void testProcessAtHour_Weekly_DoesNotFireOnNonMonday() {
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        // Wednesday – weekly subscribers should NOT be queried or triggered
        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verify(emailSubscriberRepository, never())
                .findActiveByScheduledHourAndScheduleType(anyInt(), eq(ScheduleType.WEEKLY), any());
        verifyNoInteractions(scheduledJobRunService, scheduledJobExecutor);
    }

    @Test
    void testProcessAtHour_DisabledWorkspace_SkipsGroup() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setId(UUID.randomUUID());
        Workspace disabled = new Workspace();
        disabled.setId(UUID.randomUUID());
        disabled.setStatus(WorkspaceStatus.DISABLED);
        sub.setWorkspace(disabled);
        sub.setFilter(filter1);

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(List.of(sub));

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verifyNoInteractions(scheduledJobRunService, scheduledJobExecutor);
    }

    @Test
    void testProcessAtHour_SupersedesOlderRunsBeforeCreating() {
        stubRunCreation();
        EmailSubscriber sub = new EmailSubscriber();
        sub.setId(UUID.randomUUID());
        sub.setWorkspace(workspace1);
        sub.setFilter(filter1);

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(List.of(sub));

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verify(scheduledJobRunService).supersedeOlderRuns(eq(workspace1.getId()), eq(filter1.getId()), any());
    }

    @Test
    void testProcessAtHour_DuplicateJobKey_ExecutorNeverCalled() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setId(UUID.randomUUID());
        sub.setWorkspace(workspace1);
        sub.setFilter(filter1);

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(List.of(sub));
        when(scheduledJobRunService.createScheduledRun(any(), any(), any(), anyList(), anyInt()))
                .thenReturn(Optional.empty()); // duplicate tick — creation gate rejected it

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verifyNoInteractions(scheduledJobExecutor);
    }

    @Test
    void testPollAtHour_DelegatesToProcessAtHour() {
        PollingService spy = spy(pollingService);
        doNothing().when(spy).processAtHour(anyInt(), any());

        spy.pollAtHour();

        verify(spy, times(1)).processAtHour(anyInt(), any());
    }

    @Test
    void testRunNow_CreatesManualRunAndExecutesIt() {
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setId(UUID.randomUUID());
        subscriber.setWorkspace(workspace1);
        subscriber.setFilter(filter1);
        ScheduledJobRun manualRun = ScheduledJobRun.builder().id(UUID.randomUUID()).build();
        when(scheduledJobRunService.createManualRun(subscriber.getId(), workspace1.getId(), filter1.getId(), List.of(subscriber.getId())))
                .thenReturn(Optional.of(manualRun));

        pollingService.runNow(subscriber);

        verify(scheduledJobExecutor).execute(manualRun.getId());
    }

    @Test
    void testRunNow_DuplicateManualRun_ExecutorNeverCalled() {
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setId(UUID.randomUUID());
        subscriber.setWorkspace(workspace1);
        subscriber.setFilter(filter1);
        when(scheduledJobRunService.createManualRun(any(), any(), any(), anyList()))
                .thenReturn(Optional.empty());

        pollingService.runNow(subscriber);

        verifyNoInteractions(scheduledJobExecutor);
    }
}
