package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.hpe.adm.nga.sdk.model.EntityModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    // Top of every hour: 1:00, 2:00, 3:00 ...
    @Scheduled(cron = "0 0 * * * *")
    public void pollHourly() {
        processByFrequency(Frequency.HOURLY);
    }

    // Midnight every day
    @Scheduled(cron = "0 0 0 * * ?")
    public void pollDaily() {
        processByFrequency(Frequency.DAILY);
    }

    // Midnight every Monday
    @Scheduled(cron = "0 0 0 * * MON")
    public void pollWeekly() {
        processByFrequency(Frequency.WEEKLY);
    }

    public void processByFrequency(Frequency frequency) {
        log.info("Polling for frequency: {}", frequency);
        List<EmailSubscriber> subscribers = emailSubscriberRepository.findByFrequencyAndStatus(frequency, Status.ACTIVE);

        // Group by Workspace ID and Filter ID
        Map<UUID, Map<UUID, List<EmailSubscriber>>> groupedSubscribers = subscribers.stream()
                .collect(Collectors.groupingBy(
                        sub -> sub.getWorkspace().getId(),
                        Collectors.groupingBy(sub -> sub.getFilter().getId())
                ));

        for (Map.Entry<UUID, Map<UUID, List<EmailSubscriber>>> workspaceEntry : groupedSubscribers.entrySet()) {
            for (Map.Entry<UUID, List<EmailSubscriber>> filterEntry : workspaceEntry.getValue().entrySet()) {
                List<EmailSubscriber> targetSubscribers = filterEntry.getValue();
                if (targetSubscribers.isEmpty()) continue;

                EmailSubscriber representative = targetSubscribers.get(0);
                sendNotifications(representative, targetSubscribers);
            }
        }
    }

    /**
     * Immediately executes the filter for the given subscriber and sends a notification email.
     * Used by the on-demand "Run" action triggered from the UI.
     */
    public void runNow(EmailSubscriber subscriber) {
        sendNotifications(subscriber, List.of(subscriber));
    }

    private void sendNotifications(EmailSubscriber representative, List<EmailSubscriber> recipients) {
        try {
            UUID filterId    = representative.getFilter().getId();
            UUID workspaceId = representative.getWorkspace().getId();

            List<String>      fields  = filterService.getFilterFields(filterId);
            List<EntityModel> results = filterService.executeFilter(filterId, workspaceId);
            int               limit   = filterService.getQueryLimit();

            notificationService.processAndSendNotifications(recipients, results, fields, limit);
        } catch (Exception e) {
            log.error("Failed to fetch or send notifications for filter {} / workspace {}",
                    representative.getFilter().getId(), representative.getWorkspace().getId(), e);
        }
    }
}
