package com.anushibinj.veemailer.service.job;

import com.anushibinj.veemailer.model.ScheduledJobRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    @Scheduled(fixedDelayString = "${veemailer.jobs.retry.poll-interval-ms:60000}")
    public void processDueRetries() {
        scheduledJobRunService.sweepStaleDispatching();

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
