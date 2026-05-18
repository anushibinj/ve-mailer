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

    List<EmailSubscriber> findByRecipientEmail(String email);

    // Using FETCH JOIN to eagerly load Filter and avoid N+1 issues when getting filterTitle
    @Query("SELECT e FROM EmailSubscriber e JOIN FETCH e.filter WHERE e.workspace.id = :workspaceId AND e.status = :status")
    List<EmailSubscriber> findByWorkspaceIdAndStatus(@Param("workspaceId") UUID workspaceId, @Param("status") Status status);

    /** Finds all active subscribers that have :hour in their scheduled hours and match the given schedule type. */
    @Query("SELECT DISTINCT e FROM EmailSubscriber e JOIN e.scheduledHours h WHERE h = :hour AND e.scheduleType = :scheduleType AND e.status = :status")
    List<EmailSubscriber> findActiveByScheduledHourAndScheduleType(@Param("hour") int hour, @Param("scheduleType") ScheduleType scheduleType, @Param("status") Status status);

    /** Used by the migration runner to find legacy subscribers not yet on the new schedule model. */
    List<EmailSubscriber> findByScheduleTypeIsNull();
}
