package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.TriageSlaThreshold;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.NotificationService;
import com.anushibinj.veemailer.service.TriageSlaPolicy;
import com.hpe.adm.nga.sdk.model.EntityModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes one {@link ScheduledJobRun} end to end: claim -> fetch (classify failures for
 * retry) -> dispatch gate -> prepare + dispatch. See the resilient-digest-jobs design doc for
 * the full guarantee this enforces — most importantly that dispatch only ever runs once a job
 * instance has fetched successfully, so a retried job never re-sends mail that already went out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobExecutor {

    private final ScheduledJobRunService scheduledJobRunService;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final MailAuditLogRepository mailAuditLogRepository;
    private final FilterService filterService;
    private final NotificationService notificationService;
    private final TransientFailureClassifier classifier;
    private final Clock clock;

    @Value("${veemailer.jobs.retry.interval-minutes:15}")
    private int retryIntervalMinutes;

    /** 0 disables suppression entirely. Kept below 60 by convention — see application.properties. */
    @Value("${veemailer.jobs.min-dispatch-gap-minutes:30}")
    private int minDispatchGapMinutes;

    /**
     * Attempts to run the given job. A no-op if the run cannot be found, or if the claim gate is
     * lost (someone else — a concurrent tick, a concurrent retry — already owns this attempt).
     */
    public void execute(UUID jobRunId) {
        Optional<ScheduledJobRun> maybeRun = scheduledJobRunService.findById(jobRunId);
        if (maybeRun.isEmpty()) {
            log.warn("Job run {} not found — skipping", jobRunId);
            return;
        }

        if (!scheduledJobRunService.claim(jobRunId)) {
            log.debug("Job run {} could not be claimed — already running or no longer eligible", jobRunId);
            return;
        }

        // Reload post-claim so attemptCount/claimedAt reflect this attempt.
        ScheduledJobRun run = scheduledJobRunService.findById(jobRunId).orElseThrow();

        List<EmailSubscriber> subscribers = loadLiveSubscribers(run.getSubscriberIds());
        if (subscribers.isEmpty()) {
            log.info("Job run {} has no live subscribers left — marking SUCCEEDED with nothing to do", run.getId());
            scheduledJobRunService.markSucceeded(run);
            return;
        }

        Workspace workspace = subscribers.get(0).getWorkspace();
        String filterTitle = subscribers.get(0).getFilter() != null ? subscribers.get(0).getFilter().getTitle() : null;

        List<EntityModel> results;
        List<String> fields;
        int limit;
        try {
            fields = filterService.getFilterFields(run.getFilterId());
            results = filterService.executeFilter(run.getFilterId(), run.getWorkspaceId());
            limit = filterService.getQueryLimit();
        } catch (Exception e) {
            handleFetchFailure(run, subscribers, workspace, filterTitle, e);
            return;
        }

        if (results.isEmpty()) {
            notificationService.recordSkippedNoTickets(run.getId(), subscribers, workspace, filterTitle);
            scheduledJobRunService.markSucceeded(run);
            return;
        }

        // Check A — in-flight (job level): another run for this (workspace, filter) is actively
        // RUNNING/DISPATCHING right now. Its audit rows are not written yet, so check B below
        // cannot see it — this is the check that catches the true overlap window.
        if (scheduledJobRunService.hasOtherActiveRun(run.getWorkspaceId(), run.getFilterId(), run.getId())) {
            log.warn("Job run {} suppressed — another run for workspace {} filter {} is already in-flight",
                    run.getId(), run.getWorkspaceId(), run.getFilterId());
            notificationService.recordSuppressed(run.getId(), subscribers, workspace, filterTitle,
                    "Suppressed: another run for this workspace/filter is already in progress.");
            scheduledJobRunService.markSuppressed(run);
            return;
        }

        // Check B — recency (subscription level), evaluated per subscription immediately before
        // dispatch: skip any subscription that already received a digest within the minimum gap.
        // Precise per subscription rather than per batch, so a tick covering subscribers an
        // earlier run did not still mails those subscribers.
        List<EmailSubscriber> toSend = subscribers;
        if (minDispatchGapMinutes > 0) {
            toSend = applyMinimumGapSuppression(run.getId(), subscribers, workspace, filterTitle);
            if (toSend.isEmpty()) {
                log.warn("Job run {} suppressed — every subscription already received a digest within the last {} minutes",
                        run.getId(), minDispatchGapMinutes);
                scheduledJobRunService.markSuppressed(run);
                return;
            }
        }

        if (!scheduledJobRunService.beginDispatch(run.getId())) {
            log.warn("Job run {} lost the dispatch gate — another attempt already dispatched or completed", run.getId());
            return;
        }

        try {
            dispatchToSubscribers(run.getId(), toSend, results, fields, limit, workspace, filterTitle);
            scheduledJobRunService.markSucceeded(run);
        } catch (Exception e) {
            // NotificationService.dispatch catches per-recipient failures internally and never
            // throws in practice; this is a last-resort guard. We are past the dispatch gate, so
            // this must never be retried — we cannot know how many emails already went out.
            log.error("Unexpected error while dispatching job run {}", run.getId(), e);
            scheduledJobRunService.markFailed(run, "Unexpected dispatch error: " + e.getMessage());
        }
    }

    private void handleFetchFailure(ScheduledJobRun run, List<EmailSubscriber> subscribers,
                                    Workspace workspace, String filterTitle, Exception e) {
        boolean transientFailure = classifier.isTransient(e);
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (transientFailure && run.getAttemptCount() < run.getMaxAttempts()) {
            Instant nextRetryAt = clock.instant().plus(Duration.ofMinutes(retryIntervalMinutes));
            scheduledJobRunService.markAwaitingRetry(run, message, nextRetryAt);
            notificationService.recordRetryingForAll(run.getId(), subscribers, workspace, filterTitle,
                    run.getAttemptCount(), retryIntervalMinutes);
            log.warn("Job run {} fetch failed (transient, attempt {}/{}) — retrying at {}: {}",
                    run.getId(), run.getAttemptCount(), run.getMaxAttempts(), nextRetryAt, message);
        } else {
            String reason = transientFailure
                    ? "Failed after " + run.getAttemptCount() + " attempts: " + message
                    : message;
            scheduledJobRunService.markFailed(run, reason);
            notificationService.recordFailureForAll(run.getId(), subscribers, workspace, filterTitle, reason);
            log.error("Job run {} failed permanently ({}): {}", run.getId(),
                    transientFailure ? "retries exhausted" : "non-retryable", reason);
        }
    }

    private void dispatchToSubscribers(UUID jobRunId, List<EmailSubscriber> subscribers, List<EntityModel> results,
                                       List<String> fields, int limit, Workspace workspace, String filterTitle) {
        if (!fields.contains(TriageSlaPolicy.TRIAGE_SLA_FIELD)) {
            NotificationService.PreparedDigest digest = notificationService.prepare(results, fields, limit, workspace, filterTitle);
            notificationService.dispatch(jobRunId, subscribers, digest, workspace, filterTitle);
            return;
        }

        Map<TriageSlaThreshold, List<EmailSubscriber>> byThreshold = subscribers.stream()
                .collect(Collectors.groupingBy(
                        sub -> resolveThreshold(sub.getTriageSlaThreshold()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<TriageSlaThreshold, List<EmailSubscriber>> entry : byThreshold.entrySet()) {
            TriageSlaThreshold threshold = entry.getKey();
            List<EntityModel> filteredResults = filterResultsByThreshold(results, threshold);
            if (threshold != TriageSlaThreshold.GREEN && filteredResults.isEmpty()) {
                continue;
            }
            if (filteredResults.isEmpty()) {
                notificationService.recordSkippedNoTickets(jobRunId, entry.getValue(), workspace, filterTitle);
                continue;
            }
            NotificationService.PreparedDigest digest = notificationService.prepare(filteredResults, fields, limit, workspace, filterTitle);
            notificationService.dispatch(jobRunId, entry.getValue(), digest, workspace, filterTitle);
        }
    }

    private List<EntityModel> filterResultsByThreshold(List<EntityModel> results, TriageSlaThreshold threshold) {
        TriageSlaThreshold effectiveThreshold = resolveThreshold(threshold);
        if (effectiveThreshold == TriageSlaThreshold.GREEN) {
            return results;
        }
        return results.stream()
                .filter(entity -> TriageSlaPolicy.meetsThreshold(entity, effectiveThreshold))
                .collect(Collectors.toList());
    }

    private TriageSlaThreshold resolveThreshold(TriageSlaThreshold threshold) {
        return threshold == null ? TriageSlaThreshold.GREEN : threshold;
    }

    /**
     * Splits subscribers into those to actually mail and those whose last SUCCESS digest is still
     * within the minimum gap — the latter get a SKIPPED audit row and are dropped from dispatch.
     */
    private List<EmailSubscriber> applyMinimumGapSuppression(UUID jobRunId, List<EmailSubscriber> subscribers,
                                                              Workspace workspace, String filterTitle) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofMinutes(minDispatchGapMinutes));
        List<EmailSubscriber> toSend = new ArrayList<>();
        for (EmailSubscriber subscriber : subscribers) {
            boolean recentlyDelivered = mailAuditLogRepository.existsBySubscriptionIdAndDeliveryStatusAndSentAtAfter(
                    subscriber.getId(), DeliveryStatus.SUCCESS, cutoff);
            if (!recentlyDelivered) {
                toSend.add(subscriber);
                continue;
            }
            long minutesAgo = mailAuditLogRepository
                    .findFirstBySubscriptionIdAndDeliveryStatusOrderBySentAtDesc(subscriber.getId(), DeliveryStatus.SUCCESS)
                    .map(MailAuditLog::getSentAt)
                    .map(sentAt -> Duration.between(sentAt, now).toMinutes())
                    .orElse(0L);
            String reason = "Suppressed: a digest for this subscription was delivered " + minutesAgo + " minutes ago.";
            notificationService.recordSuppressed(jobRunId, List.of(subscriber), workspace, filterTitle, reason);
        }
        return toSend;
    }

    /**
     * Reloads subscribers fresh from the DB by id, dropping any since deleted, disabled, or whose
     * workspace went DISABLED — a retry must not email someone who unsubscribed in the meantime.
     */
    private List<EmailSubscriber> loadLiveSubscribers(List<UUID> subscriberIds) {
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return List.of();
        }
        return emailSubscriberRepository.findAllById(subscriberIds).stream()
                .filter(sub -> sub.getStatus() == Status.ACTIVE)
                .filter(sub -> sub.getWorkspace() != null && sub.getWorkspace().getStatus() != WorkspaceStatus.DISABLED)
                .collect(Collectors.toList());
    }
}
