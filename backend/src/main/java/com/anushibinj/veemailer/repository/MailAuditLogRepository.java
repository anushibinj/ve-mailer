package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MailAuditLogRepository extends JpaRepository<MailAuditLog, UUID>, JpaSpecificationExecutor<MailAuditLog> {

    // --- Summary aggregations ---

    long countBySentAtAfter(Instant since);

    @Query("SELECT COUNT(DISTINCT m.recipientEmail) FROM MailAuditLog m WHERE m.sentAt > :since")
    long countUniqueRecipientsSince(@Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT m.workspaceId) FROM MailAuditLog m WHERE m.sentAt > :since")
    long countActiveWorkspacesSince(@Param("since") Instant since);

    // --- Daily volume ---

    @Query("""
        SELECT CAST(m.sentAt AS date) AS day, COUNT(m) AS cnt
        FROM MailAuditLog m
        WHERE m.sentAt > :since
        GROUP BY CAST(m.sentAt AS date)
        ORDER BY day
        """)
    List<Object[]> countDailyVolumeSince(@Param("since") Instant since);

    // --- Unique recipients per day ---

    @Query("""
        SELECT CAST(m.sentAt AS date) AS day, COUNT(DISTINCT m.recipientEmail) AS cnt
        FROM MailAuditLog m
        WHERE m.sentAt > :since
        GROUP BY CAST(m.sentAt AS date)
        ORDER BY day
        """)
    List<Object[]> countDailyUniqueRecipientsSince(@Param("since") Instant since);

    // --- Workspace distribution ---

    @Query("""
        SELECT m.workspaceTitle, COUNT(m) AS cnt
        FROM MailAuditLog m
        WHERE m.sentAt > :since AND m.workspaceTitle IS NOT NULL
        GROUP BY m.workspaceTitle
        ORDER BY cnt DESC
        """)
    List<Object[]> countByWorkspaceSince(@Param("since") Instant since);

    // --- Filter usage ---

    @Query("""
        SELECT m.filterTitle, COUNT(m) AS cnt
        FROM MailAuditLog m
        WHERE m.sentAt > :since AND m.filterTitle IS NOT NULL
        GROUP BY m.filterTitle
        ORDER BY cnt DESC
        """)
    List<Object[]> countByFilterSince(@Param("since") Instant since);

    // --- Top filter (most used) ---

    @Query("""
        SELECT m.filterTitle FROM MailAuditLog m
        WHERE m.sentAt > :since AND m.filterTitle IS NOT NULL
        GROUP BY m.filterTitle
        ORDER BY COUNT(m) DESC
        LIMIT 1
        """)
    String findTopFilterSince(@Param("since") Instant since);

    // --- Paginated history with filters ---
    // Dynamic filtering is done via JpaSpecificationExecutor in MailAuditLogSpecs.
    // This avoids the Hibernate 6 / PostgreSQL issue where (:param IS NULL OR ...) patterns
    // fail with "could not determine data type of parameter" for null bindings.

    // --- Job-run-aware upserts: one row per (jobRunId, subscriptionId, recipientEmail) ---

    Optional<MailAuditLog> findByJobRunIdAndSubscriptionIdAndRecipientEmail(
            UUID jobRunId, UUID subscriptionId, String recipientEmail);

    /** Flips every still-RETRYING row of a job run to a terminal status (supersede / max-attempts-reached). */
    @Modifying
    @Query("UPDATE MailAuditLog m SET m.deliveryStatus = :status, m.failureReason = :reason " +
            "WHERE m.jobRunId = :jobRunId AND m.deliveryStatus = com.anushibinj.veemailer.model.DeliveryStatus.RETRYING")
    int flipRetryingToTerminal(@Param("jobRunId") UUID jobRunId,
                               @Param("status") DeliveryStatus status,
                               @Param("reason") String reason);

    /** Minimum-dispatch-gap recency check (B): has this subscription already received a digest recently? */
    boolean existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
            UUID subscriptionId, DeliveryStatus deliveryStatus, Instant sentAfter);

    /** Used once the check above trips, to compute the exact "delivered N minutes ago" wording. */
    Optional<MailAuditLog> findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(
            UUID subscriptionId, DeliveryStatus deliveryStatus);
}
