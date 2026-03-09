package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollingServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RestTemplate restTemplate;

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
    void testProcessByFrequency_GroupsCorrectly() {
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

        List<EmailSubscriber> subscribers = Arrays.asList(sub1, sub2, sub3, sub4);

        when(emailSubscriberRepository.findByFrequencyAndStatus(Frequency.HOURLY, Status.ACTIVE))
                .thenReturn(subscribers);

        pollingService.processByFrequency(Frequency.HOURLY);

        // Group 1: sub1, sub2
        // Group 2: sub3
        // Group 3: sub4
        verify(notificationService, times(3)).processAndSendNotifications(anyList(), anyString());
    }

    @Test
    void testProcessByFrequency_Empty() {
        when(emailSubscriberRepository.findByFrequencyAndStatus(Frequency.DAILY, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        pollingService.processByFrequency(Frequency.DAILY);

        verify(notificationService, never()).processAndSendNotifications(anyList(), anyString());
    }

    @Test
    void testScheduledMethods() {
        // Just verify they run the inner process
        PollingService spy = spy(pollingService);
        doNothing().when(spy).processByFrequency(any());

        spy.pollHourly();
        verify(spy).processByFrequency(Frequency.HOURLY);

        spy.pollDaily();
        verify(spy).processByFrequency(Frequency.DAILY);

        spy.pollWeekly();
        verify(spy).processByFrequency(Frequency.WEEKLY);
    }

    @Test
    void testFetchExternalData_ReturnsMockJson() {
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setWorkspace(workspace1);
        subscriber.setFilter(filter1);

        String result = pollingService.fetchExternalData(subscriber);

        assertEquals("{ \"tickets\": [{\"id\": 1, \"title\": \"Mock Ticket\"}] }", result);
    }

    @Test
    void testFetchExternalData_ExceptionReturnsEmptyJson() {
        // subscriber with null workspace triggers NullPointerException inside fetchExternalData,
        // which should be caught and return "{}"
        EmailSubscriber subscriber = new EmailSubscriber();
        subscriber.setWorkspace(null);
        subscriber.setFilter(filter1);

        String result = pollingService.fetchExternalData(subscriber);

        assertEquals("{}", result);
    }
}
