package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.JobRunStatus;
import com.anushibinj.veemailer.model.JobTriggerType;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.repository.ScheduledJobRunRepository;
import com.anushibinj.veemailer.service.MailAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the {@link ScheduledJobRun} lifecycle: creation (the creation gate — a duplicate
 * {@code job_key} means a tick was already handled), claiming (the claim gate — at most one
 * active retrier per job), dispatch gating (at most one send per job instance), superseding a
 * stuck retrier when the next scheduled slot arrives, and recovering runs whose process died
 * mid-dispatch. See the resilient-digest-jobs design doc for the full guarantee this enforces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobRunService {

    private final ScheduledJobRunRepository repository;
    private final MailAuditService mailAuditService;
    private final Clock clock;

    @Value("${veemailer.jobs.retry.stale-claim-minutes:30}")
    private long staleClaimMinutes;

    /** Creates the one job instance for this (workspaceId, filterId, slotAt) triple, or empty if it already exists. */
    public Optional<ScheduledJobRun> createScheduledRun(UUID workspaceId, UUID filterId, Instant slotAt,
                                                         List<UUID> subscriberIds, int maxAttempts) {
        String jobKey = "SCHEDULED:" + workspaceId + ":" + filterId + ":" + slotAt.getEpochSecond();
        return createRun(jobKey, workspaceId, filterId, null, slotAt, JobTriggerType.SCHEDULED, subscriberIds, maxAttempts);
    }

    /** Creates a one-shot manual run for the on-demand "Run now" action. Retries are disabled (maxAttempts = 1). */
    public Optional<ScheduledJobRun> createManualRun(UUID subscriptionId, UUID workspaceId, UUID filterId,
                                                      List<UUID> subscriberIds) {
        Instant now = clock.instant();
        String jobKey = "MANUAL:" + subscriptionId + ":" + now.toEpochMilli();
        return createRun(jobKey, workspaceId, filterId, subscriptionId, now, JobTriggerType.MANUAL, subscriberIds, 1);
    }

    private Optional<ScheduledJobRun> createRun(String jobKey, UUID workspaceId, UUID filterId, UUID subscriptionId,
                                                 Instant slotAt, JobTriggerType triggerType,
                                                 List<UUID> subscriberIds, int maxAttempts) {
        Instant now = clock.instant();
        ScheduledJobRun run = ScheduledJobRun.builder()
                .jobKey(jobKey)
                .workspaceId(workspaceId)
                .filterId(filterId)
                .subscriptionId(subscriptionId)
                .slotAt(slotAt)
                .triggerType(triggerType)
                .status(JobRunStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .subscriberIds(subscriberIds)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            return Optional.of(repository.saveAndFlush(run));
        } catch (DataIntegrityViolationException e) {
            log.info("Job run already exists for key '{}' — skipping duplicate creation", jobKey);
            return Optional.empty();
        }
    }

    /** Claim gate: only the caller whose call returns true may execute this run. */
    public boolean claim(UUID runId) {
        return repository.claim(runId, clock.instant()) == 1;
    }

    /** Dispatch gate: RUNNING -> DISPATCHING, committed before any Transport.send. */
    public boolean beginDispatch(UUID runId) {
        return repository.beginDispatch(runId, clock.instant()) == 1;
    }

    /** In-flight check A: is another run for this (workspaceId, filterId) actively RUNNING/DISPATCHING? */
    public boolean hasOtherActiveRun(UUID workspaceId, UUID filterId, UUID excludeRunId) {
        Instant staleBefore = staleClaimBefore();
        return repository.existsOtherActiveRun(workspaceId, filterId, excludeRunId, staleBefore);
    }

    /**
     * Marks every non-terminal older run for the same (workspaceId, filterId) as FAILED, so a
     * stuck retrier never fires after the next scheduled slot has taken over. A genuinely
     * executing RUNNING run (non-stale claim) is left alone — its own dispatch gate is the
     * safety net if it turns out to be stuck after all.
     */
    @Transactional
    public List<ScheduledJobRun> supersedeOlderRuns(UUID workspaceId, UUID filterId, Instant slotAt) {
        List<ScheduledJobRun> candidates = repository.findSupersedeCandidates(
                workspaceId, filterId, slotAt, staleClaimBefore());
        Instant now = clock.instant();
        for (ScheduledJobRun run : candidates) {
            run.setStatus(JobRunStatus.FAILED);
            run.setLastError("Superseded by the next scheduled run");
            run.setUpdatedAt(now);
            repository.save(run);
            mailAuditService.markJobRunTerminal(run.getId(), DeliveryStatus.FAILED, "Superseded by the next scheduled run");
        }
        return candidates;
    }

    public void markSucceeded(ScheduledJobRun run) {
        Instant now = clock.instant();
        run.setStatus(JobRunStatus.SUCCEEDED);
        run.setDispatchCompletedAt(now);
        run.setUpdatedAt(now);
        repository.save(run);
    }

    public void markFailed(ScheduledJobRun run, String error) {
        Instant now = clock.instant();
        run.setStatus(JobRunStatus.FAILED);
        run.setLastError(truncate(error));
        run.setUpdatedAt(now);
        repository.save(run);
    }

    public void markSuppressed(ScheduledJobRun run) {
        Instant now = clock.instant();
        run.setStatus(JobRunStatus.SUPPRESSED);
        run.setUpdatedAt(now);
        repository.save(run);
    }

    public void markAwaitingRetry(ScheduledJobRun run, String error, Instant nextRetryAt) {
        Instant now = clock.instant();
        run.setStatus(JobRunStatus.AWAITING_RETRY);
        run.setLastError(truncate(error));
        run.setNextRetryAt(nextRetryAt);
        run.setUpdatedAt(now);
        repository.save(run);
    }

    public List<ScheduledJobRun> findDueForRetry() {
        return repository.findDueForRetry(clock.instant());
    }

    /**
     * Recovery sweep: a run stuck in DISPATCHING for longer than the stale-claim window almost
     * certainly means the process died mid-fan-out. It is marked FAILED and — critically — never
     * retried, because we cannot know how many of its emails already went out.
     */
    @Transactional
    public List<ScheduledJobRun> sweepStaleDispatching() {
        List<ScheduledJobRun> stale = repository.findStaleDispatching(staleClaimBefore());
        for (ScheduledJobRun run : stale) {
            log.error("Job run {} stuck in DISPATCHING since {} — marking FAILED without retry " +
                    "(process likely died mid-dispatch)", run.getId(), run.getDispatchStartedAt());
            markFailed(run, "Recovered: process died mid-dispatch; never retried to avoid duplicate sends");
        }
        return stale;
    }

    private Instant staleClaimBefore() {
        return clock.instant().minus(Duration.ofMinutes(staleClaimMinutes));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
