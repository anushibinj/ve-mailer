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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FilterRepository filterRepository;
    private final PollingService pollingService;

    /**
     * Creates (or upserts) a subscription for the authenticated user.
     * The email is derived exclusively from the JWT security context — never from the request payload.
     */
    public SubscriptionResponseDTO createSubscription(String email, UUID workspaceId, UUID filterId, ScheduleDto schedule) {
        validateSchedule(schedule);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));

        Optional<EmailSubscriber> existingOpt = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, workspace.getId(), filter.getId());

        EmailSubscriber subscriber = existingOpt.orElse(new EmailSubscriber());
        subscriber.setRecipientEmail(email);
        subscriber.setWorkspace(workspace);
        subscriber.setFilter(filter);
        applySchedule(subscriber, schedule);
        subscriber.setStatus(Status.ACTIVE);

        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    /**
     * Updates the schedule for an existing subscription owned by the authenticated user.
     * Enforces ownership: throws AccessDeniedException if the subscription belongs to another user.
     */
    public SubscriptionResponseDTO updateSubscription(String email, UUID subscriptionId, UUID workspaceId, ScheduleDto schedule) {
        validateSchedule(schedule);

        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceOwnership(email, subscriber);
        enforceWorkspace(workspaceId, subscriber);

        applySchedule(subscriber, schedule);
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    /**
     * Deletes a subscription owned by the authenticated user.
     * Enforces ownership: throws AccessDeniedException if the subscription belongs to another user.
     */
    public void deleteSubscription(String email, UUID subscriptionId, UUID workspaceId) {
        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceOwnership(email, subscriber);
        enforceWorkspace(workspaceId, subscriber);

        emailSubscriberRepository.delete(subscriber);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptionsForWorkspace(UUID workspaceId) {
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findByWorkspaceIdAndStatus(workspaceId, Status.ACTIVE);
        return subscribers.stream().map(this::toResponseDto).collect(Collectors.toList());
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

    // --- Private helpers ---

    private void enforceOwnership(String email, EmailSubscriber subscriber) {
        if (!subscriber.getRecipientEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Access denied: you do not own this subscription");
        }
    }

    private void enforceWorkspace(UUID workspaceId, EmailSubscriber subscriber) {
        if (!subscriber.getWorkspace().getId().equals(workspaceId)) {
            throw new IllegalArgumentException("Subscription does not belong to the specified workspace");
        }
    }

    private void applySchedule(EmailSubscriber subscriber, ScheduleDto schedule) {
        subscriber.setScheduleType(schedule.getType());
        // Deduplicate while preserving insertion order
        List<Integer> deduped = new ArrayList<>(new LinkedHashSet<>(schedule.getHours()));
        subscriber.setScheduledHours(deduped);
        subscriber.setFrequency(null); // clear legacy field
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

    private SubscriptionResponseDTO toResponseDto(EmailSubscriber sub) {
        return SubscriptionResponseDTO.builder()
                .id(sub.getId())
                .recipientEmail(sub.getRecipientEmail())
                .filterId(sub.getFilter().getId())
                .filterTitle(sub.getFilter().getTitle())
                .schedule(buildScheduleDto(sub))
                .build();
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

