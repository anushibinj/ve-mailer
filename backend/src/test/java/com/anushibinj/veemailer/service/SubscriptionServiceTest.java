package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.OtpRequest;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private OtpService otpService;

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private FilterRepository filterRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private UUID workspaceId;
    private UUID filterId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        filterId = UUID.randomUUID();
    }

    @Test
    void testRequestSubscription_Success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("mock-json-payload");

        subscriptionService.requestSubscription("test@test.com", ActionType.SUBSCRIBE, workspaceId, filterId, Frequency.HOURLY);

        verify(otpService, times(1)).createAndSendOtp(eq("test@test.com"), eq(ActionType.SUBSCRIBE), eq("mock-json-payload"));
    }

    @Test
    void testVerifyAndExecute_Subscribe() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.SUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.DAILY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);

        Workspace ws = new Workspace();
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));

        Filter f = new Filter();
        f.setId(filterId);
        when(filterRepository.findById(filterId)).thenReturn(Optional.of(f));

        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.empty());

        subscriptionService.verifyAndExecute("test@test.com", "123456");

        ArgumentCaptor<EmailSubscriber> captor = ArgumentCaptor.forClass(EmailSubscriber.class);
        verify(emailSubscriberRepository, times(1)).save(captor.capture());

        EmailSubscriber saved = captor.getValue();
        assertEquals("test@test.com", saved.getRecipientEmail());
        assertEquals(Frequency.DAILY, saved.getFrequency());
        assertEquals(Status.ACTIVE, saved.getStatus());

        verify(otpService, times(1)).cleanupOtp(request);
    }

    @Test
    void testVerifyAndExecute_Update() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.UPDATE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.WEEKLY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);

        EmailSubscriber existing = new EmailSubscriber();
        existing.setFrequency(Frequency.DAILY);

        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.of(existing));

        subscriptionService.verifyAndExecute("test@test.com", "123456");

        ArgumentCaptor<EmailSubscriber> captor = ArgumentCaptor.forClass(EmailSubscriber.class);
        verify(emailSubscriberRepository, times(1)).save(captor.capture());

        assertEquals(Frequency.WEEKLY, captor.getValue().getFrequency());
        verify(otpService, times(1)).cleanupOtp(request);
    }

    @Test
    void testVerifyAndExecute_Unsubscribe() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.UNSUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);

        EmailSubscriber existing = new EmailSubscriber();

        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.of(existing));

        subscriptionService.verifyAndExecute("test@test.com", "123456");

        verify(emailSubscriberRepository, times(1)).delete(existing);
        verify(otpService, times(1)).cleanupOtp(request);
    }

    @Test
    void testGetActiveSubscriptionsForWorkspace() {
        Filter f = new Filter();
        f.setTitle("Urgent Bugs");

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("dev@test.com");
        sub.setFilter(f);
        sub.setFrequency(Frequency.HOURLY);

        when(emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE))
                .thenReturn(Arrays.asList(sub));

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId);

        assertEquals(1, results.size());
        assertEquals("dev@test.com", results.get(0).getRecipientEmail());
        assertEquals("Urgent Bugs", results.get(0).getFilterTitle());
        assertEquals(Frequency.HOURLY, results.get(0).getFrequency());
    }

    @Test
    void testRequestSubscription_JsonProcessingException_ThrowsRuntimeException() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialization error") {});

        assertThrows(RuntimeException.class, () ->
                subscriptionService.requestSubscription("test@test.com", ActionType.SUBSCRIBE, workspaceId, filterId, Frequency.DAILY));
    }

    @Test
    void testVerifyAndExecute_Subscribe_WorkspaceNotFound() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.SUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.DAILY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.verifyAndExecute("test@test.com", "123456"));
    }

    @Test
    void testVerifyAndExecute_Subscribe_FilterNotFound() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.SUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.DAILY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);

        Workspace ws = new Workspace();
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));
        when(filterRepository.findById(filterId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.verifyAndExecute("test@test.com", "123456"));
    }

    @Test
    void testVerifyAndExecute_Subscribe_ExistingSubscriberUpsert() throws JsonProcessingException {
        // When subscriber already exists, should update (upsert) rather than create new
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.SUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.WEEKLY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);

        Workspace ws = new Workspace();
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));

        Filter f = new Filter();
        f.setId(filterId);
        when(filterRepository.findById(filterId)).thenReturn(Optional.of(f));

        // Existing subscriber
        EmailSubscriber existing = new EmailSubscriber();
        existing.setRecipientEmail("test@test.com");
        existing.setFrequency(Frequency.DAILY);
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.of(existing));

        subscriptionService.verifyAndExecute("test@test.com", "123456");

        ArgumentCaptor<EmailSubscriber> captor = ArgumentCaptor.forClass(EmailSubscriber.class);
        verify(emailSubscriberRepository, times(1)).save(captor.capture());

        // Should be the same existing object, updated
        assertEquals(Frequency.WEEKLY, captor.getValue().getFrequency());
        assertEquals(Status.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void testVerifyAndExecute_Update_SubscriptionNotFound() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.UPDATE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);
        payload.setFrequency(Frequency.WEEKLY);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.verifyAndExecute("test@test.com", "123456"));
    }

    @Test
    void testVerifyAndExecute_Unsubscribe_SubscriptionNotFound() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.UNSUBSCRIBE);
        request.setPayload("mock-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);

        SubscriptionService.SubscriptionPayload payload = new SubscriptionService.SubscriptionPayload();
        payload.setWorkspaceId(workspaceId);
        payload.setFilterId(filterId);

        when(objectMapper.readValue("mock-json", SubscriptionService.SubscriptionPayload.class)).thenReturn(payload);
        when(emailSubscriberRepository.findByRecipientEmailAndWorkspaceIdAndFilterId("test@test.com", workspaceId, filterId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.verifyAndExecute("test@test.com", "123456"));
    }

    @Test
    void testVerifyAndExecute_JsonProcessingException_ThrowsRuntimeException() throws JsonProcessingException {
        OtpRequest request = new OtpRequest();
        request.setActionType(ActionType.SUBSCRIBE);
        request.setPayload("bad-json");

        when(otpService.validateOtp("test@test.com", "123456")).thenReturn(request);
        when(objectMapper.readValue("bad-json", SubscriptionService.SubscriptionPayload.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("parse error") {});

        assertThrows(RuntimeException.class, () ->
                subscriptionService.verifyAndExecute("test@test.com", "123456"));
    }

    @Test
    void testGetActiveSubscriptionsForWorkspace_EmptyList() {
        when(emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<SubscriptionResponseDTO> results = subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
