package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MailAuditLogRepository extends JpaRepository<MailAuditLog, UUID> {

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

    @Query("""
        SELECT m FROM MailAuditLog m
        WHERE (:workspaceId IS NULL OR m.workspaceId = :workspaceId)
          AND (:recipientEmail IS NULL OR m.recipientEmail = :recipientEmail)
          AND (:filterTitle IS NULL OR LOWER(m.filterTitle) LIKE LOWER(CONCAT('%', :filterTitle, '%')))
          AND (:status IS NULL OR m.deliveryStatus = :status)
          AND (:from IS NULL OR m.sentAt >= :from)
          AND (:to IS NULL OR m.sentAt <= :to)
        """)
    Page<MailAuditLog> findFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientEmail") String recipientEmail,
            @Param("filterTitle") String filterTitle,
            @Param("status") DeliveryStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
