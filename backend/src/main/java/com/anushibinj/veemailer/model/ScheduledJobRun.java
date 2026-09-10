package com.anushibinj.veemailer.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One job instance: the unit of work for a single (workspaceId, filterId, slotAt) triple —
 * exactly one Octane query and one rendered digest. {@code jobKey} is the creation-time
 * idempotency gate (DB-level UNIQUE constraint); {@code status}/{@code claimedAt} back the
 * claim gate; {@code dispatchStartedAt} backs the dispatch gate. See the feature design doc
 * ("Job identity and the three idempotency gates") for the full guarantee this enforces.
 */
@Entity
@Table(name = "scheduled_job_run")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_key", nullable = false, unique = true)
    private String jobKey;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "filter_id")
    private UUID filterId;

    /** Set only for MANUAL runs — the single subscription the "Run now" action targeted. */
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "slot_at", nullable = false)
    private Instant slotAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private JobTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobRunStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /** Committed before the first Transport.send — the dispatch gate. Never re-claimable. */
    @Column(name = "dispatch_started_at")
    private Instant dispatchStartedAt;

    @Column(name = "dispatch_completed_at")
    private Instant dispatchCompletedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    /**
     * The subscriber ids this run must notify, persisted so a retry can reload them fresh
     * from the DB (dropping any deleted/disabled subscriber) rather than trusting a stale
     * in-memory list from the failed attempt.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scheduled_job_run_subscriber_ids",
            joinColumns = @JoinColumn(name = "job_run_id",
                    foreignKey = @ForeignKey(name = "fk_job_run_subscriber_ids_job_run_id")))
    @Column(name = "subscriber_id")
    private List<UUID> subscriberIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
