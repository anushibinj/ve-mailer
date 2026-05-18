package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migrates legacy frequency-based subscriptions to the new schedule model on application startup.
 * Runs once at boot; subscribers already on the new model (scheduleType is not null) are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleMigrationRunner implements ApplicationRunner {

    private final EmailSubscriberRepository emailSubscriberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<EmailSubscriber> legacy = emailSubscriberRepository.findByScheduleTypeIsNull();
        if (legacy.isEmpty()) {
            return;
        }

        log.info("Migrating {} legacy subscription(s) to the new schedule model", legacy.size());

        for (EmailSubscriber sub : legacy) {
            if (sub.getFrequency() == null) {
                // No frequency either – set a safe default (daily at 08:00)
                sub.setScheduleType(ScheduleType.DAILY);
                sub.setScheduledHours(List.of(8));
                continue;
            }
            switch (sub.getFrequency()) {
                case HOURLY -> {
                    // Map hourly to four evenly-spaced daily notifications
                    sub.setScheduleType(ScheduleType.DAILY);
                    sub.setScheduledHours(List.of(0, 6, 12, 18));
                }
                case DAILY -> {
                    sub.setScheduleType(ScheduleType.DAILY);
                    sub.setScheduledHours(List.of(8));
                }
                case WEEKLY -> {
                    sub.setScheduleType(ScheduleType.WEEKLY);
                    sub.setScheduledHours(List.of(8));
                }
            }
        }

        emailSubscriberRepository.saveAll(legacy);
        log.info("Migration complete for {} subscription(s)", legacy.size());
    }
}
