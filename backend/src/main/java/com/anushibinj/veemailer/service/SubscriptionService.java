package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.RecipientGroup;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.TriageSlaThreshold;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.RecipientGroupRepository;
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
    private final RecipientGroupRepository recipientGroupRepository;
    private final PollingService pollingService;

    /**
     * Creates (or upserts) a subscription for the authenticated user.
     * The email is derived exclusively from the JWT security context — never from the request payload.
     */
    public SubscriptionResponseDTO createSubscription(String email, UUID workspaceId, UUID filterId, ScheduleDto schedule) {
        return createSubscription(email, workspaceId, filterId, schedule, null);
    }

    public SubscriptionResponseDTO createSubscription(
            String email, UUID workspaceId, UUID filterId, ScheduleDto schedule, TriageSlaThreshold triageSlaThreshold) {
        validateSchedule(schedule);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        enforceWorkspaceNotDisabled(workspace);

        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));
        enforceFilterCanBeSubscribedByRecipient(filter, email);

        Optional<EmailSubscriber> existingOpt = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId(email, workspace.getId(), filter.getId());

        EmailSubscriber subscriber = existingOpt.orElse(new EmailSubscriber());
        subscriber.setRecipientEmail(email);
        subscriber.setWorkspace(workspace);
        subscriber.setFilter(filter);
        applySchedule(subscriber, schedule);
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(triageSlaThreshold));
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
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(null, subscriber.getTriageSlaThreshold()));
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    public SubscriptionResponseDTO updateSubscription(
            String email, UUID subscriptionId, UUID workspaceId, ScheduleDto schedule, TriageSlaThreshold triageSlaThreshold) {
        validateSchedule(schedule);

        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceOwnership(email, subscriber);
        enforceWorkspace(workspaceId, subscriber);

        applySchedule(subscriber, schedule);
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(triageSlaThreshold, subscriber.getTriageSlaThreshold()));
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    /**
     * Updates the schedule for any subscription without ownership enforcement.
     * For use by admins and workspace admins only — authorization must be checked in the controller.
     */
    public SubscriptionResponseDTO updateSubscriptionByAdmin(UUID subscriptionId, UUID workspaceId, ScheduleDto schedule) {
        validateSchedule(schedule);

        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceWorkspace(workspaceId, subscriber);

        applySchedule(subscriber, schedule);
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(null, subscriber.getTriageSlaThreshold()));
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    public SubscriptionResponseDTO updateSubscriptionByAdmin(
            UUID subscriptionId, UUID workspaceId, ScheduleDto schedule, TriageSlaThreshold triageSlaThreshold) {
        validateSchedule(schedule);

        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceWorkspace(workspaceId, subscriber);

        applySchedule(subscriber, schedule);
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(triageSlaThreshold, subscriber.getTriageSlaThreshold()));
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

    /**
     * Deletes any subscription without ownership enforcement.
     * For use by admins and workspace admins only — authorization must be checked in the controller.
     */
    public void deleteSubscriptionByAdmin(UUID subscriptionId, UUID workspaceId) {
        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceWorkspace(workspaceId, subscriber);

        emailSubscriberRepository.delete(subscriber);
    }

    public List<SubscriptionResponseDTO> getActiveSubscriptionsForWorkspace(UUID workspaceId) {
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findByWorkspaceIdAndStatusIn(
                workspaceId, List.of(Status.ACTIVE, Status.DISABLED));
        return subscribers.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    /**
     * Creates (or upserts) a single group subscription row for the specified recipient group.
     * This is a first-class subscription: one row, one schedule to update.
     * At send time, the group's current member emails are expanded dynamically, so adding/removing
     * group members automatically affects who gets notified without touching this row.
     */
    public SubscriptionResponseDTO createGroupSubscription(UUID workspaceId, UUID groupId, UUID filterId, ScheduleDto schedule) {
        return createGroupSubscription(workspaceId, groupId, filterId, schedule, null);
    }

    public SubscriptionResponseDTO createGroupSubscription(
            UUID workspaceId, UUID groupId, UUID filterId, ScheduleDto schedule, TriageSlaThreshold triageSlaThreshold) {
        validateSchedule(schedule);

        RecipientGroup group = recipientGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient group not found"));

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        enforceWorkspaceNotDisabled(workspace);

        Filter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found"));
        if (!filter.isPublic() && filter.getOwnerEmail() != null) {
            throw new AccessDeniedException("Private user filters cannot be used for group subscriptions");
        }

        // Upsert: one row per (group, workspace, filter) combination
        EmailSubscriber subscriber = emailSubscriberRepository
                .findByGroupIdAndWorkspaceIdAndFilterId(groupId, workspaceId, filterId)
                .orElse(new EmailSubscriber());
        subscriber.setRecipientEmail(null);
        subscriber.setGroup(group);
        subscriber.setWorkspace(workspace);
        subscriber.setFilter(filter);
        applySchedule(subscriber, schedule);
        subscriber.setTriageSlaThreshold(resolveTriageSlaThreshold(triageSlaThreshold));
        subscriber.setStatus(Status.ACTIVE);

        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    /**
     * Returns ACTIVE and DISABLED subscriptions belonging to the authenticated user within a workspace.
     * Used for MEMBER-role requests to enforce per-user data isolation.
     * Includes both the user's own person-level subscriptions and any group subscriptions for groups
     * the user is a member of, so a MEMBER can see (read-only) the group subscriptions they receive
     * mail through — group rows can't be edited/toggled/deleted by non-admins (see enforceOwnership).
     */
    public List<SubscriptionResponseDTO> getActiveSubscriptionsForUser(String email, UUID workspaceId) {
        List<Status> statuses = List.of(Status.ACTIVE, Status.DISABLED);
        List<EmailSubscriber> personal = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndStatusIn(email, workspaceId, statuses);
        List<EmailSubscriber> groupSubs = emailSubscriberRepository
                .findGroupSubscriptionsForMemberAndWorkspaceIdAndStatusIn(email, workspaceId, statuses);

        List<EmailSubscriber> combined = new ArrayList<>(personal);
        combined.addAll(groupSubs);
        return combined.stream().map(this::toResponseDto).collect(Collectors.toList());
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

        enforceWorkspaceNotDisabled(subscriber.getWorkspace());

        pollingService.runNow(subscriber);
    }

    /**
     * Toggles the status of a subscription between ACTIVE and DISABLED.
     * Enforces ownership: throws AccessDeniedException if the subscription belongs to another user.
     */
    public SubscriptionResponseDTO toggleSubscription(String email, UUID subscriptionId, UUID workspaceId) {
        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceOwnership(email, subscriber);
        enforceWorkspace(workspaceId, subscriber);

        subscriber.setStatus(subscriber.getStatus() == Status.ACTIVE ? Status.DISABLED : Status.ACTIVE);
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    /**
     * Toggles the status of any subscription without ownership enforcement.
     * For use by admins and workspace admins only — authorization must be checked in the controller.
     */
    public SubscriptionResponseDTO toggleSubscriptionByAdmin(UUID subscriptionId, UUID workspaceId) {
        EmailSubscriber subscriber = emailSubscriberRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        enforceWorkspace(workspaceId, subscriber);

        subscriber.setStatus(subscriber.getStatus() == Status.ACTIVE ? Status.DISABLED : Status.ACTIVE);
        return toResponseDto(emailSubscriberRepository.save(subscriber));
    }

    // --- Private helpers ---

    private void enforceWorkspaceNotDisabled(Workspace workspace) {
        if (workspace.getStatus() == WorkspaceStatus.DISABLED) {
            throw new IllegalArgumentException("Workspace is disabled and cannot be used for subscriptions or mail generation");
        }
    }

    private void enforceOwnership(String email, EmailSubscriber subscriber) {
        if (subscriber.getGroup() != null) {
            // Group subscriptions are managed by admins only; regular users cannot own them
            throw new AccessDeniedException("Access denied: group subscriptions can only be managed by admins");
        }
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
                .triageSlaThreshold(resolveTriageSlaThreshold(sub.getTriageSlaThreshold()))
                .groupId(sub.getGroup() != null ? sub.getGroup().getId() : null)
                .groupName(sub.getGroup() != null ? sub.getGroup().getName() : null)
                .groupMemberCount(sub.getGroup() != null ? sub.getGroup().getMemberEmails().size() : null)
                .status(sub.getStatus())
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

    private void enforceFilterCanBeSubscribedByRecipient(Filter filter, String recipientEmail) {
        if (filter.isPublic() || filter.getOwnerEmail() == null) {
            return;
        }
        if (!filter.getOwnerEmail().equalsIgnoreCase(recipientEmail)) {
            throw new AccessDeniedException("You are not authorized to subscribe to this private filter");
        }
    }

    private TriageSlaThreshold resolveTriageSlaThreshold(TriageSlaThreshold triageSlaThreshold) {
        return resolveTriageSlaThreshold(triageSlaThreshold, null);
    }

    private TriageSlaThreshold resolveTriageSlaThreshold(
            TriageSlaThreshold triageSlaThreshold, TriageSlaThreshold currentValue) {
        if (triageSlaThreshold != null) {
            return triageSlaThreshold;
        }
        if (currentValue != null) {
            return currentValue;
        }
        return TriageSlaThreshold.GREEN;
    }
}
