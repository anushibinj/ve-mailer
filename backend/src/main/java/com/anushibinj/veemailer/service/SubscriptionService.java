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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final OtpService otpService;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FilterRepository filterRepository;
    private final ObjectMapper objectMapper;

    @Data
    public static class SubscriptionPayload {
        private UUID workspaceId;
        private UUID filterId;
        private Frequency frequency;
    }

    public void requestSubscription(String email, ActionType actionType, UUID workspaceId, UUID filterId, Frequency frequency) {
        try {
            SubscriptionPayload payload = new SubscriptionPayload();
            payload.setWorkspaceId(workspaceId);
            payload.setFilterId(filterId);
            payload.setFrequency(frequency);

            String payloadJson = objectMapper.writeValueAsString(payload);
            otpService.createAndSendOtp(email, actionType, payloadJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    public void verifyAndExecute(String email, String otp) {
        OtpRequest request = otpService.validateOtp(email, otp);

        try {
            SubscriptionPayload payload = objectMapper.readValue(request.getPayload(), SubscriptionPayload.class);

            switch (request.getActionType()) {
                case SUBSCRIBE:
                    createSubscription(email, payload);
                    break;
                case UPDATE:
                    updateSubscription(email, payload);
                    break;
                case UNSUBSCRIBE:
                    deleteSubscription(email, payload);
                    break;
            }

            otpService.cleanupOtp(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize payload", e);
        }
    }

    private void createSubscription(String email, SubscriptionPayload payload) {
        Workspace workspace = workspaceRepository.findById(payload.getWorkspaceId())
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        Filter filter = filterRepository.findById(payload.getFilterId())
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));

        Optional<EmailSubscriber> existingOpt = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, workspace.getId(), filter.getId());

        EmailSubscriber subscriber = existingOpt.orElse(new EmailSubscriber());
        subscriber.setRecipientEmail(email);
        subscriber.setWorkspace(workspace);
        subscriber.setFilter(filter);
        subscriber.setFrequency(payload.getFrequency());
        subscriber.setStatus(Status.ACTIVE);

        emailSubscriberRepository.save(subscriber);
    }

    private void updateSubscription(String email, SubscriptionPayload payload) {
        EmailSubscriber subscriber = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, payload.getWorkspaceId(), payload.getFilterId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        subscriber.setFrequency(payload.getFrequency());
        emailSubscriberRepository.save(subscriber);
    }

    private void deleteSubscription(String email, SubscriptionPayload payload) {
        EmailSubscriber subscriber = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, payload.getWorkspaceId(), payload.getFilterId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        emailSubscriberRepository.delete(subscriber);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptionsForWorkspace(UUID workspaceId) {
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE);
        return subscribers.stream().map(sub -> SubscriptionResponseDTO.builder()
                .id(sub.getId())
                .recipientEmail(sub.getRecipientEmail())
                .filterId(sub.getFilter().getId())
                .filterTitle(sub.getFilter().getTitle())
                .frequency(sub.getFrequency())
                .build()).collect(Collectors.toList());
    }
}
