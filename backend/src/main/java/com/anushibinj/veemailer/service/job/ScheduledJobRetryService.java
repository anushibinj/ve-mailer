package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.ScheduledJobRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drives retries of jobs sitting in AWAITING_RETRY, and sweeps runs whose process died mid
 * dispatch. Ticks independently of the hourly poller (dedicated scheduler thread — see
 * {@code spring.task.scheduling.pool-size} in application.properties) so a stuck retrier is
 * revisited every {@code veemailer.jobs.retry.poll-interval-ms} regardless of the hourly cron.
 *
 * <p>Exclusivity is enforced downstream: {@link ScheduledJobExecutor#execute} re-attempts the
 * claim gate for every run, so if two ticks (or a tick and a concurrent superseding run) reach
 * the same job, only one of them actually executes it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobRetryService {

    private final ScheduledJobRunService scheduledJobRunService;
    private final ScheduledJobExecutor scheduledJobExecutor;
    private final Clock clock;

    @Value("${veemailer.jobs.retry.stale-claim-minutes:30}")
    private long staleClaimMinutes;

    /**
     * Boot-time recovery: nothing can legitimately be mid-flight in a freshly started process, so
     * any PENDING/RUNNING/DISPATCHING run left over from the previous process is an orphan. Only
     * recent, pre-dispatch orphans are resumed (see {@link ScheduledJobRunService#recoverOrphanedRuns});
     * the rest are failed, never mailed late.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            Instant bootCutoff = clock.instant();
            scheduledJobRunService.sweepStaleDispatching(bootCutoff);
            scheduledJobRunService.recoverOrphanedRuns(bootCutoff);
            processDueRetries();
        } catch (Exception e) {
            log.error("Startup recovery of incomplete job runs failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${veemailer.jobs.retry.poll-interval-ms:60000}")
    public void processDueRetries() {
        scheduledJobRunService.sweepStaleDispatching();
        scheduledJobRunService.recoverOrphanedRuns(clock.instant().minus(Duration.ofMinutes(staleClaimMinutes)));
        scheduledJobRunService.expireStaleRetries();

        List<ScheduledJobRun> due = scheduledJobRunService.findDueForRetry();
        for (ScheduledJobRun run : due) {
            try {
                scheduledJobExecutor.execute(run.getId());
            } catch (Exception e) {
                // One run's unexpected failure must not stop the others in this tick from retrying.
                log.error("Unexpected error while retrying job run {}", run.getId(), e);
            }
        }
    }
}
