package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private FilterRepository filterRepository;

    @Mock
    private PollingService pollingService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private UUID workspaceId;
    private UUID filterId;
    private ScheduleDto dailySchedule;
    private Workspace workspace;
    private Filter filter;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        filterId = UUID.randomUUID();
        dailySchedule = ScheduleDto.builder()
                .type(ScheduleType.DAILY)
                .hours(List.of(9, 15))
                .build();
        workspace = new Workspace();
        workspace.setId(workspaceId);
        filter = new Filter();
        filter.setId(filterId);
        filter.setTitle("All Bugs");
    }

    // ─────────────────────────── createSubscription ───────────────────────────

    @Test
    void testCreateSubscription_NewSubscriber_Saved() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(filterRepository.findById(filterId)).thenReturn(Optional.of(filter));
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId(
                "user@test.com", workspaceId, filterId)).thenReturn(Optional.empty());

        EmailSubscriber saved = new EmailSubscriber();
        saved.setId(UUID.randomUUID());
        saved.setRecipientEmail("user@test.com");
        saved.setWorkspace(workspace);
        saved.setFilter(filter);
        saved.setScheduleType(ScheduleType.DAILY);
        saved.setScheduledHours(List.of(9, 15));
        saved.setStatus(Status.ACTIVE);
        when(emailSubscriberRepository.save(any())).thenReturn(saved);

        SubscriptionResponseDTO result = subscriptionService.createSubscription(
                "user@test.com", workspaceId, filterId, dailySchedule);

        assertNotNull(result);
        assertEquals("user@test.com", result.getRecipientEmail());
        assertEquals("All Bugs", result.getFilterTitle());
        assertEquals(ScheduleType.DAILY, result.getSchedule().getType());
    }

    @Test
    void testCreateSubscription_ExistingSubscriber_Upserts() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(filterRepository.findById(filterId)).thenReturn(Optional.of(filter));

        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("user@test.com");
        existing.setWorkspace(workspace);
        existing.setFilter(filter);
        existing.setScheduleType(ScheduleType.WEEKLY);
        existing.setScheduledHours(List.of(8));
        existing.setStatus(Status.ACTIVE);
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId(
                "user@test.com", workspaceId, filterId)).thenReturn(Optional.of(existing));
        when(emailSubscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.createSubscription("user@test.com", workspaceId, filterId, dailySchedule);

        ArgumentCaptor<EmailSubscriber> captor = ArgumentCaptor.forClass(EmailSubscriber.class);
        verify(emailSubscriberRepository, times(1)).save(captor.capture());
        assertEquals(ScheduleType.DAILY, captor.getValue().getScheduleType());
        assertEquals(List.of(9, 15), captor.getValue().getScheduledHours());
        assertNull(captor.getValue().getFrequency());
        assertEquals(Status.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void testCreateSubscription_WorkspaceNotFound_Throws() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.createSubscription("user@test.com", workspaceId, filterId, dailySchedule));
    }

    @Test
    void testCreateSubscription_FilterNotFound_Throws() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(filterRepository.findById(filterId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.createSubscription("user@test.com", workspaceId, filterId, dailySchedule));
    }

    @Test
    void testCreateSubscription_DuplicateHours_Throws() {
        ScheduleDto dup = ScheduleDto.builder()
                .type(ScheduleType.DAILY).hours(List.of(9, 9)).build();

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.createSubscription("user@test.com", workspaceId, filterId, dup));
    }

    @Test
    void testCreateSubscription_InvalidHour_Throws() {
        ScheduleDto bad = ScheduleDto.builder()
                .type(ScheduleType.DAILY).hours(List.of(24)).build();

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.createSubscription("user@test.com", workspaceId, filterId, bad));
    }

    @Test
    void testCreateSubscription_EmptyHours_Throws() {
        ScheduleDto empty = ScheduleDto.builder()
                .type(ScheduleType.DAILY).hours(Collections.emptyList()).build();

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.createSubscription("user@test.com", workspaceId, filterId, empty));
    }

    // ─────────────────────────── updateSubscription ───────────────────────────

    @Test
    void testUpdateSubscription_Success() {
        UUID subscriptionId = UUID.randomUUID();
        EmailSubscriber existing = new EmailSubscriber();
        existing.setId(subscriptionId);
        existing.setRecipientEmail("user@test.com");
        existing.setWorkspace(workspace);
        existing.setFilter(filter);
        existing.setScheduleType(ScheduleType.DAILY);
        existing.setScheduledHours(List.of(9));
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.of(existing));
        when(emailSubscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleDto newSchedule = ScheduleDto.builder()
                .type(ScheduleType.WEEKLY).hours(List.of(10)).build();
        subscriptionService.updateSubscription("user@test.com", subscriptionId, workspaceId, newSchedule);

        ArgumentCaptor<EmailSubscriber> captor = ArgumentCaptor.forClass(EmailSubscriber.class);
        verify(emailSubscriberRepository).save(captor.capture());
        assertEquals(ScheduleType.WEEKLY, captor.getValue().getScheduleType());
        assertEquals(List.of(10), captor.getValue().getScheduledHours());
    }

    @Test
    void testUpdateSubscription_NotFound_Throws() {
        UUID subscriptionId = UUID.randomUUID();
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.updateSubscription("user@test.com", subscriptionId, workspaceId, dailySchedule));
    }

    @Test
    void testUpdateSubscription_WrongOwner_ThrowsAccessDenied() {
        UUID subscriptionId = UUID.randomUUID();
        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("other@test.com");
        existing.setWorkspace(workspace);
        existing.setFilter(filter);
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class, () ->
                subscriptionService.updateSubscription("user@test.com", subscriptionId, workspaceId, dailySchedule));
    }

    @Test
    void testUpdateSubscription_WrongWorkspace_Throws() {
        UUID subscriptionId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();
        Workspace otherWorkspace = new Workspace();
        otherWorkspace.setId(otherWorkspaceId);

        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("user@test.com");
        existing.setWorkspace(otherWorkspace);
        existing.setFilter(filter);
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.updateSubscription("user@test.com", subscriptionId, workspaceId, dailySchedule));
    }

    // ─────────────────────────── deleteSubscription ───────────────────────────

    @Test
    void testDeleteSubscription_Success() {
        UUID subscriptionId = UUID.randomUUID();
        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("user@test.com");
        existing.setWorkspace(workspace);
        existing.setFilter(filter);
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.of(existing));

        subscriptionService.deleteSubscription("user@test.com", subscriptionId, workspaceId);

        verify(emailSubscriberRepository).delete(existing);
    }

    @Test
    void testDeleteSubscription_NotFound_Throws() {
        UUID subscriptionId = UUID.randomUUID();
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.deleteSubscription("user@test.com", subscriptionId, workspaceId));
    }

    @Test
    void testDeleteSubscription_WrongOwner_ThrowsAccessDenied() {
        UUID subscriptionId = UUID.randomUUID();
        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("other@test.com");
        existing.setWorkspace(workspace);
        existing.setFilter(filter);
        when(emailSubscriberRepository.findById(subscriptionId)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class, () ->
                subscriptionService.deleteSubscription("user@test.com", subscriptionId, workspaceId));
    }

    // ─────────────────────── getActiveSubscriptionsForWorkspace ───────────────

    @Test
    void testGetActiveSubscriptionsForWorkspace_ReturnsMappedDtos() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("dev@test.com");
        sub.setFilter(filter);
        sub.setScheduleType(ScheduleType.DAILY);
        sub.setScheduledHours(List.of(9, 15));

        when(emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE))
                .thenReturn(List.of(sub));

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId);

        assertEquals(1, results.size());
        assertEquals("dev@test.com", results.get(0).getRecipientEmail());
        assertEquals("All Bugs", results.get(0).getFilterTitle());
        assertEquals(ScheduleType.DAILY, results.get(0).getSchedule().getType());
        assertEquals(List.of(9, 15), results.get(0).getSchedule().getHours());
    }

    @Test
    void testGetActiveSubscriptionsForWorkspace_LegacySubscriber_FallsBackToDefault() {
        Filter legacyFilter = new Filter();
        legacyFilter.setTitle("Legacy Filter");

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("legacy@test.com");
        sub.setFilter(legacyFilter);
        sub.setScheduleType(null);
        sub.setScheduledHours(null);

        when(emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE))
                .thenReturn(List.of(sub));

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId);

        assertEquals(1, results.size());
        assertEquals(ScheduleType.DAILY, results.get(0).getSchedule().getType());
        assertEquals(List.of(0), results.get(0).getSchedule().getHours());
    }

    @Test
    void testGetActiveSubscriptionsForWorkspace_EmptyList() {
        when(emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ─────────────────────── getActiveSubscriptionsForUser ───────────────────

    @Test
    void testGetActiveSubscriptionsForUser_ReturnsOnlyUserSubscriptions() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("user@test.com");
        sub.setFilter(filter);
        sub.setScheduleType(ScheduleType.DAILY);
        sub.setScheduledHours(List.of(9));

        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndStatus("user@test.com", workspaceId, Status.ACTIVE))
                .thenReturn(List.of(sub));

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForUser("user@test.com", workspaceId);

        assertEquals(1, results.size());
        assertEquals("user@test.com", results.get(0).getRecipientEmail());
        assertEquals("All Bugs", results.get(0).getFilterTitle());
        assertEquals(ScheduleType.DAILY, results.get(0).getSchedule().getType());
        assertEquals(List.of(9), results.get(0).getSchedule().getHours());
    }

    @Test
    void testGetActiveSubscriptionsForUser_EmptyWhenNoSubscriptions() {
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndStatus("user@test.com", workspaceId, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForUser("user@test.com", workspaceId);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
