package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSubscriberRepository extends JpaRepository<EmailSubscriber, UUID> {

    /** Legacy – used only by the backward-compat migration runner. */
    List<EmailSubscriber> findByFrequencyAndStatus(Frequency frequency, Status status);

    Optional<EmailSubscriber> findByRecipientEmailAndWorkspaceIdAndFilterId(String recipientEmail, UUID workspaceId, UUID filterId);

    /** Finds an existing group subscription (dedup: one row per group+workspace+filter). */
    @Query("SELECT e FROM EmailSubscriber e WHERE e.group.id = :groupId AND e.workspace.id = :workspaceId AND e.filter.id = :filterId")
    Optional<EmailSubscriber> findByGroupIdAndWorkspaceIdAndFilterId(
            @Param("groupId") UUID groupId,
            @Param("workspaceId") UUID workspaceId,
            @Param("filterId") UUID filterId);

    List<EmailSubscriber> findByRecipientEmail(String email);

    void deleteByRecipientEmail(String email);

    // Using FETCH JOIN to eagerly load Filter and avoid N+1 issues when getting filterTitle
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter WHERE e.workspace.id = :workspaceId AND e.status = :status")
    List<EmailSubscriber> findByWorkspaceIdAndStatus(@Param("workspaceId") UUID workspaceId, @Param("status") Status status);

    /** Returns ACTIVE and DISABLED subscriptions for a workspace (for listing in the UI). */
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter WHERE e.workspace.id = :workspaceId AND e.status IN :statuses")
    List<EmailSubscriber> findByWorkspaceIdAndStatusIn(@Param("workspaceId") UUID workspaceId, @Param("statuses") List<Status> statuses);

    /** Returns active subscriptions for a specific user within a workspace (used for MEMBER-role isolation). */
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter WHERE e.recipientEmail = :email AND e.workspace.id = :workspaceId AND e.status = :status")
    List<EmailSubscriber> findByRecipientEmailAndWorkspaceIdAndStatus(@Param("email") String email, @Param("workspaceId") UUID workspaceId, @Param("status") Status status);

    /** Returns ACTIVE and DISABLED subscriptions for a user within a workspace (for listing in the UI). */
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter WHERE e.recipientEmail = :email AND e.workspace.id = :workspaceId AND e.status IN :statuses")
    List<EmailSubscriber> findByRecipientEmailAndWorkspaceIdAndStatusIn(@Param("email") String email, @Param("workspaceId") UUID workspaceId, @Param("statuses") List<Status> statuses);

    /**
     * Returns ACTIVE and DISABLED group subscriptions within a workspace where the given email is a
     * member of the subscribed recipient group. Used so a MEMBER can see (read-only) the group
     * subscriptions they receive mail through, in addition to their own person-level subscriptions.
     */
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter JOIN e.group g JOIN g.memberEmails m " +
            "WHERE e.workspace.id = :workspaceId AND m = :email AND e.status IN :statuses")
    List<EmailSubscriber> findGroupSubscriptionsForMemberAndWorkspaceIdAndStatusIn(
            @Param("email") String email, @Param("workspaceId") UUID workspaceId, @Param("statuses") List<Status> statuses);

    /** Finds all active subscribers that have :hour in their scheduled hours and match the given schedule type. */
    @Query("SELECT DISTINCT e FROM EmailSubscriber e JOIN e.scheduledHours h WHERE h = :hour AND e.scheduleType = :scheduleType AND e.status = :status")
    List<EmailSubscriber> findActiveByScheduledHourAndScheduleType(@Param("hour") int hour, @Param("scheduleType") ScheduleType scheduleType, @Param("status") Status status);

    /** Used by the migration runner to find legacy subscribers not yet on the new schedule model. */
    List<EmailSubscriber> findByScheduleTypeIsNull();

    /** Deletes all subscriptions associated with the given filter. Used when a filter template is deleted. */
    void deleteByFilter_Id(UUID filterId);

    /**
     * Returns a list of [recipientEmail, count] pairs counting the number of active subscriptions
     * (distinct filters) per user email. Group subscriptions (recipientEmail IS NULL) are excluded.
     */
    @Query("SELECT e.recipientEmail, COUNT(DISTINCT e.filter.id) FROM EmailSubscriber e WHERE e.status = 'ACTIVE' AND e.recipientEmail IS NOT NULL GROUP BY e.recipientEmail")
    List<Object[]> countActiveSubscriptionsGroupedByEmail();
}
