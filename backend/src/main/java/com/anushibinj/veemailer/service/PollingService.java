package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.ScheduledJobRun;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.service.job.ScheduledJobExecutor;
import com.anushibinj.veemailer.service.job.ScheduledJobRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fires hourly, groups due subscribers by (workspace, filter) into job instances, and hands each
 * one to {@link ScheduledJobRunService}/{@link ScheduledJobExecutor}. All fetch/retry/dispatch
 * logic lives in the executor — this class only decides *what* runs and *when*.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PollingService {

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final ScheduledJobRunService scheduledJobRunService;
    private final ScheduledJobExecutor scheduledJobExecutor;
    private final Clock clock;

    @Value("${veemailer.jobs.retry.max-attempts:4}")
    private int maxAttempts;

    /** Runs at the top of every hour (second=0, minute=0). */
    @Scheduled(cron = "0 0 * * * *")
    public void pollAtHour() {
        processAtHour(LocalTime.now().getHour(), LocalDate.now().getDayOfWeek());
    }

    /**
     * Processes all subscriptions scheduled for the given hour on the given day.
     * Package-private to allow direct testing without mocking system time.
     */
    void processAtHour(int hour, DayOfWeek dayOfWeek) {
        log.info("Processing scheduled notifications for hour={} day={}", hour, dayOfWeek);

        List<EmailSubscriber> dailySubscribers = emailSubscriberRepository
                .findActiveByScheduledHourAndScheduleType(hour, ScheduleType.DAILY, Status.ACTIVE);
        processSubscriberList(dailySubscribers);

        // Weekly subscriptions fire only on Mondays
        if (dayOfWeek == DayOfWeek.MONDAY) {
            List<EmailSubscriber> weeklySubscribers = emailSubscriberRepository
                    .findActiveByScheduledHourAndScheduleType(hour, ScheduleType.WEEKLY, Status.ACTIVE);
            processSubscriberList(weeklySubscribers);
        }
    }

    /**
     * Immediately executes the filter for the given subscriber and sends a notification email.
     * Group subscriptions send one email to all current group members. Used by the on-demand
     * "Run" action triggered from the UI. Creates a MANUAL job run (maxAttempts = 1, no retries)
     * so a double-click or two admins clicking at once cannot double-send.
     */
    public void runNow(EmailSubscriber subscriber) {
        UUID workspaceId = subscriber.getWorkspace().getId();
        UUID filterId = subscriber.getFilter().getId();
        scheduledJobRunService.createManualRun(subscriber.getId(), workspaceId, filterId, List.of(subscriber.getId()))
                .ifPresent(run -> scheduledJobExecutor.execute(run.getId()));
    }

    private void processSubscriberList(List<EmailSubscriber> subscribers) {
        if (subscribers.isEmpty()) {
            return;
        }

        // Filter out subscribers belonging to DISABLED workspaces
        List<EmailSubscriber> activeSubscribers = subscribers.stream()
                .filter(sub -> sub.getWorkspace().getStatus() != WorkspaceStatus.DISABLED)
                .collect(Collectors.toList());
        if (activeSubscribers.isEmpty()) {
            return;
        }

        // Group by Workspace ID and Filter ID to batch notifications (one filter query per batch,
        // and one job instance per group)
        Map<UUID, Map<UUID, List<EmailSubscriber>>> grouped = activeSubscribers.stream()
                .collect(Collectors.groupingBy(
                        sub -> sub.getWorkspace().getId(),
                        Collectors.groupingBy(sub -> sub.getFilter().getId())
                ));

        // One slotAt per tick: re-running processAtHour within the same instant (a duplicate cron
        // fire, or a second app instance) produces the same job_key for every group, so the
        // creation gate naturally dedupes the whole tick rather than just one group.
        Instant slotAt = clock.instant().truncatedTo(ChronoUnit.HOURS);

        for (Map.Entry<UUID, Map<UUID, List<EmailSubscriber>>> workspaceEntry : grouped.entrySet()) {
            for (Map.Entry<UUID, List<EmailSubscriber>> filterEntry : workspaceEntry.getValue().entrySet()) {
                createAndRunScheduledJob(workspaceEntry.getKey(), filterEntry.getKey(), filterEntry.getValue(), slotAt);
            }
        }
    }

    private void createAndRunScheduledJob(UUID workspaceId, UUID filterId, List<EmailSubscriber> group, Instant slotAt) {
        scheduledJobRunService.supersedeOlderRuns(workspaceId, filterId, slotAt);
        List<UUID> subscriberIds = group.stream().map(EmailSubscriber::getId).collect(Collectors.toList());
        scheduledJobRunService.createScheduledRun(workspaceId, filterId, slotAt, subscriberIds, maxAttempts)
                .ifPresent(this::executeNewRun);
    }

    private void executeNewRun(ScheduledJobRun run) {
        scheduledJobExecutor.execute(run.getId());
    }
}
