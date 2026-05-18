package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.OtpRequest;
import com.anushibinj.veemailer.model.ScheduleType;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final PollingService pollingService;

    @Data
    public static class SubscriptionPayload {
        private UUID workspaceId;
        private UUID filterId;
        private ScheduleDto schedule;
    }

    public void requestSubscription(String email, ActionType actionType, UUID workspaceId, UUID filterId, ScheduleDto schedule) {
        validateSchedule(schedule);
        try {
            SubscriptionPayload payload = new SubscriptionPayload();
            payload.setWorkspaceId(workspaceId);
            payload.setFilterId(filterId);
            payload.setSchedule(schedule);

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
        applySchedule(subscriber, payload.getSchedule());
        subscriber.setStatus(Status.ACTIVE);

        emailSubscriberRepository.save(subscriber);
    }

    private void updateSubscription(String email, SubscriptionPayload payload) {
        EmailSubscriber subscriber = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, payload.getWorkspaceId(), payload.getFilterId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        applySchedule(subscriber, payload.getSchedule());
        emailSubscriberRepository.save(subscriber);
    }

    private void deleteSubscription(String email, SubscriptionPayload payload) {
        EmailSubscriber subscriber = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, payload.getWorkspaceId(), payload.getFilterId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        emailSubscriberRepository.delete(subscriber);
    }

    private void applySchedule(EmailSubscriber subscriber, ScheduleDto schedule) {
        subscriber.setScheduleType(schedule.getType());
        // Deduplicate while preserving insertion order
        List<Integer> deduped = new ArrayList<>(new LinkedHashSet<>(schedule.getHours()));
        subscriber.setScheduledHours(deduped);
        subscriber.setFrequency(null); // clear legacy field
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptionsForWorkspace(UUID workspaceId) {
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE);
        return subscribers.stream().map(sub -> SubscriptionResponseDTO.builder()
                .id(sub.getId())
                .recipientEmail(sub.getRecipientEmail())
                .filterId(sub.getFilter().getId())
                .filterTitle(sub.getFilter().getTitle())
                .schedule(buildScheduleDto(sub))
                .build()).collect(Collectors.toList());
    }

    private ScheduleDto buildScheduleDto(EmailSubscriber sub) {
        if (sub.getScheduleType() != null) {
            return ScheduleDto.builder()
                    .type(sub.getScheduleType())
                    .hours(sub.getScheduledHours() != null ? sub.getScheduledHours() : List.of())
                    .build();
        }
        // Legacy fallback: subscriber has not been migrated yet – derive a safe default
        return ScheduleDto.builder()
                .type(ScheduleType.DAILY)
                .hours(List.of(0))
                .build();
    }

    /**
     * Immediately sends a notification email for a single subscription on demand.
     * Validates that the subscription belongs to the given workspace before running.
     */
    public void runSubscription(UUID subscriptionId, UUID workspaceId) {
        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (!subscriber.getWorkspace().getId().equals(workspaceId)) {
            throw new IllegalArgumentException("Subscription does not belong to the specified workspace");
        }

        pollingService.runNow(subscriber);
    }

    private void validateSchedule(ScheduleDto schedule) {
        if (schedule == null || schedule.getHours() == null || schedule.getHours().isEmpty()) {
            throw new IllegalArgumentException("Schedule hours must not be empty");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer hour : schedule.getHours()) {
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Hour must be between 0 and 23, got: " + hour);
            }
            if (!seen.add(hour)) {
                throw new IllegalArgumentException("Duplicate hour in schedule: " + hour);
            }
        }
    }
}
