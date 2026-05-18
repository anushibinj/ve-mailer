package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.hpe.adm.nga.sdk.model.EntityModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
    private NotificationService notificationService;

    @Mock
    private FilterService filterService;

    @InjectMocks
    private PollingService pollingService;

    private Workspace workspace1;
    private Workspace workspace2;
    private Filter filter1;
    private Filter filter2;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setId(UUID.randomUUID());
        workspace1.setClientId("client-id-1");
        workspace1.setClientKey("client-key-1");

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

    @Test
    void testProcessAtHour_Daily_GroupsCorrectly() {
        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setWorkspace(workspace1);
        sub1.setFilter(filter1);

        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setWorkspace(workspace1);
        sub2.setFilter(filter1); // Same group as sub1

        EmailSubscriber sub3 = new EmailSubscriber();
        sub3.setWorkspace(workspace1);
        sub3.setFilter(filter2); // Different filter

        EmailSubscriber sub4 = new EmailSubscriber();
        sub4.setWorkspace(workspace2);
        sub4.setFilter(filter1); // Different workspace

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Arrays.asList(sub1, sub2, sub3, sub4));
        when(filterService.getFilterFields(any())).thenReturn(List.of("name"));
        when(filterService.executeFilter(any(), any())).thenReturn(Collections.emptyList());
        when(filterService.getQueryLimit()).thenReturn(25);

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        // Group 1: sub1, sub2  |  Group 2: sub3  |  Group 3: sub4
        verify(notificationService, times(3))
                .processAndSendNotifications(anyList(), anyList(), anyList(), anyInt());
    }

    @Test
    void testProcessAtHour_Empty_NoNotificationsSent() {
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verify(notificationService, never())
                .processAndSendNotifications(anyList(), anyList(), anyList(), anyInt());
    }

    @Test
    void testProcessAtHour_Weekly_FiresOnlyOnMonday() {
        EmailSubscriber weeklySub = new EmailSubscriber();
        weeklySub.setWorkspace(workspace1);
        weeklySub.setFilter(filter1);

        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.WEEKLY, Status.ACTIVE))
                .thenReturn(List.of(weeklySub));
        when(filterService.getFilterFields(any())).thenReturn(List.of("name"));
        when(filterService.executeFilter(any(), any())).thenReturn(Collections.emptyList());
        when(filterService.getQueryLimit()).thenReturn(25);

        pollingService.processAtHour(9, DayOfWeek.MONDAY);

        verify(notificationService, times(1))
                .processAndSendNotifications(anyList(), anyList(), anyList(), anyInt());
    }

    @Test
    void testProcessAtHour_Weekly_DoesNotFireOnNonMonday() {
        when(emailSubscriberRepository.findActiveByScheduledHourAndScheduleType(9, ScheduleType.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        // Wednesday – weekly subscribers should NOT be queried or triggered
        pollingService.processAtHour(9, DayOfWeek.WEDNESDAY);

        verify(emailSubscriberRepository, never())
                .findActiveByScheduledHourAndScheduleType(anyInt(), eq(ScheduleType.WEEKLY), any());
        verify(notificationService, never())
                .processAndSendNotifications(anyList(), anyList(), anyList(), anyInt());
    }

    @Test
    void testPollAtHour_DelegatesToProcessAtHour() {
        PollingService spy = spy(pollingService);
        doNothing().when(spy).processAtHour(anyInt(), any());

        spy.pollAtHour();

        verify(spy, times(1)).processAtHour(anyInt(), any());
    }

    @Test
    void testRunNow_CallsFilterServiceAndNotification() {
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setWorkspace(workspace1);
        subscriber.setFilter(filter1);

        List<String>      fields  = List.of("name", "phase");
        List<EntityModel> results = Collections.emptyList();

        when(filterService.getFilterFields(filter1.getId())).thenReturn(fields);
        when(filterService.executeFilter(filter1.getId(), workspace1.getId())).thenReturn(results);
        when(filterService.getQueryLimit()).thenReturn(25);

        pollingService.runNow(subscriber);

        verify(filterService).getFilterFields(filter1.getId());
        verify(filterService).executeFilter(filter1.getId(), workspace1.getId());
        verify(notificationService).processAndSendNotifications(List.of(subscriber), results, fields, 25);
    }

    @Test
    void testRunNow_FilterServiceException_LogsAndDoesNotThrow() {
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setWorkspace(workspace1);
        subscriber.setFilter(filter1);

        when(filterService.getFilterFields(any())).thenThrow(new RuntimeException("Octane error"));

        // Should not propagate — error is logged internally
        assertDoesNotThrow(() -> pollingService.runNow(subscriber));
        verify(notificationService, never()).processAndSendNotifications(anyList(), anyList(), anyList(), anyInt());
    }

    // Bring in assertDoesNotThrow
    private static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            throw new AssertionError("Expected no exception but got: " + t, t);
        }
    }
}
