package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, UUID> {

    Optional<ScheduledJobRun> findByJobKey(String jobKey);

    /**
     * Claim gate: only the caller whose update returns 1 may proceed to execute this run.
     * Bumps attemptCount so the cap (maxAttempts) is enforced from the claim itself.
     */
    @Modifying
    @Query("UPDATE ScheduledJobRun r SET r.status = com.anushibinj.veemailer.model.JobRunStatus.RUNNING, " +
            "r.claimedAt = :now, r.attemptCount = r.attemptCount + 1, r.updatedAt = :now " +
            "WHERE r.id = :id AND r.status IN (com.anushibinj.veemailer.model.JobRunStatus.PENDING, " +
            "com.anushibinj.veemailer.model.JobRunStatus.AWAITING_RETRY)")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Dispatch gate: RUNNING -> DISPATCHING. Committed before any Transport.send. DISPATCHING
     * is a terminal-for-claiming state — never re-claimable — so at most one caller ever wins this.
     */
    @Modifying
    @Query("UPDATE ScheduledJobRun r SET r.status = com.anushibinj.veemailer.model.JobRunStatus.DISPATCHING, " +
            "r.dispatchStartedAt = :now, r.updatedAt = :now " +
            "WHERE r.id = :id AND r.status = com.anushibinj.veemailer.model.JobRunStatus.RUNNING")
    int beginDispatch(@Param("id") UUID id, @Param("now") Instant now);

    /** Runs eligible for the next retry attempt. */
    @Query("SELECT r FROM ScheduledJobRun r WHERE r.status = com.anushibinj.veemailer.model.JobRunStatus.AWAITING_RETRY " +
            "AND r.nextRetryAt <= :now")
    List<ScheduledJobRun> findDueForRetry(@Param("now") Instant now);

    /**
     * Candidates for superseding: same (workspaceId, filterId), an older slotAt, not yet terminal.
     * PENDING/AWAITING_RETRY are always eligible; a RUNNING run is only eligible once its claim is
     * stale (a genuinely executing run is left alone — its dispatch gate is the safety net).
     */
    @Query("SELECT r FROM ScheduledJobRun r WHERE r.workspaceId = :workspaceId AND r.filterId = :filterId " +
            "AND r.slotAt < :slotAt AND (" +
            "  r.status IN (com.anushibinj.veemailer.model.JobRunStatus.PENDING, com.anushibinj.veemailer.model.JobRunStatus.AWAITING_RETRY) " +
            "  OR (r.status = com.anushibinj.veemailer.model.JobRunStatus.RUNNING AND r.claimedAt < :staleClaimBefore)" +
            ")")
    List<ScheduledJobRun> findSupersedeCandidates(@Param("workspaceId") UUID workspaceId,
                                                   @Param("filterId") UUID filterId,
                                                   @Param("slotAt") Instant slotAt,
                                                   @Param("staleClaimBefore") Instant staleClaimBefore);

    /**
     * In-flight check A: is another run for this (workspaceId, filterId) currently RUNNING or
     * DISPATCHING with a non-stale claim? Used at dispatch time to catch true overlap windows
     * before either run's audit rows exist for check B to see.
     */
    @Query("SELECT COUNT(r) > 0 FROM ScheduledJobRun r WHERE r.workspaceId = :workspaceId AND r.filterId = :filterId " +
            "AND r.id <> :excludeId AND r.claimedAt >= :staleClaimBefore " +
            "AND r.status IN (com.anushibinj.veemailer.model.JobRunStatus.RUNNING, com.anushibinj.veemailer.model.JobRunStatus.DISPATCHING)")
    boolean existsOtherActiveRun(@Param("workspaceId") UUID workspaceId,
                                  @Param("filterId") UUID filterId,
                                  @Param("excludeId") UUID excludeId,
                                  @Param("staleClaimBefore") Instant staleClaimBefore);

    /** Recovery sweep: DISPATCHING runs whose process apparently died mid-fan-out. */
    @Query("SELECT r FROM ScheduledJobRun r WHERE r.status = com.anushibinj.veemailer.model.JobRunStatus.DISPATCHING " +
            "AND r.dispatchStartedAt < :before")
    List<ScheduledJobRun> findStaleDispatching(@Param("before") Instant before);

    List<ScheduledJobRun> findByStatus(JobRunStatus status);
}
