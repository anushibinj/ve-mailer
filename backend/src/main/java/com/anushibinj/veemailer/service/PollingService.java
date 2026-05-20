package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.hpe.adm.nga.sdk.model.EntityModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollingService {

    private final EmailSubscriberRepository emailSubscriberRepository;
    private final NotificationService notificationService;
    private final FilterService filterService;

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
     * Used by the on-demand "Run" action triggered from the UI.
     */
    public void runNow(EmailSubscriber subscriber) {
        sendNotifications(subscriber, List.of(subscriber));
    }

    private void processSubscriberList(List<EmailSubscriber> subscribers) {
        if (subscribers.isEmpty()) return;

        // Group by Workspace ID and Filter ID to batch notifications
        Map<UUID, Map<UUID, List<EmailSubscriber>>> grouped = subscribers.stream()
                .collect(Collectors.groupingBy(
                        sub -> sub.getWorkspace().getId(),
                        Collectors.groupingBy(sub -> sub.getFilter().getId())
                ));

        for (Map.Entry<UUID, Map<UUID, List<EmailSubscriber>>> workspaceEntry : grouped.entrySet()) {
            for (Map.Entry<UUID, List<EmailSubscriber>> filterEntry : workspaceEntry.getValue().entrySet()) {
                List<EmailSubscriber> targetSubscribers = filterEntry.getValue();
                if (targetSubscribers.isEmpty()) continue;

                EmailSubscriber representative = targetSubscribers.get(0);
                sendNotifications(representative, targetSubscribers);
            }
        }
    }

    private void sendNotifications(EmailSubscriber representative, List<EmailSubscriber> recipients) {
        try {
            UUID filterId    = representative.getFilter().getId();
            UUID workspaceId = representative.getWorkspace().getId();

            List<String>      fields     = filterService.getFilterFields(filterId);
            List<EntityModel> results    = filterService.executeFilter(filterId, workspaceId);
            int               limit      = filterService.getQueryLimit();
            String            filterTitle = representative.getFilter().getTitle();

            notificationService.processAndSendNotifications(recipients, results, fields, limit, representative.getWorkspace(), filterTitle);
        } catch (Exception e) {
            log.error("Failed to fetch or send notifications for filter {} / workspace {}",
                    representative.getFilter().getId(), representative.getWorkspace().getId(), e);
        }
    }
}
