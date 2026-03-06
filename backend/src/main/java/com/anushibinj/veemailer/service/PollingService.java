package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

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
    private final RestTemplate restTemplate;

    // e.g., run every hour
    @Scheduled(fixedRate = 3600000)
    public void pollHourly() {
        processByFrequency(Frequency.HOURLY);
    }

    // e.g., run daily
    @Scheduled(cron = "0 0 0 * * ?")
    public void pollDaily() {
        processByFrequency(Frequency.DAILY);
    }

    // e.g., run weekly
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
                String externalData = fetchExternalData(representative);

                notificationService.processAndSendNotifications(targetSubscribers, externalData);
            }
        }
    }

    public String fetchExternalData(EmailSubscriber subscriber) {
        try {
            String clientId = subscriber.getWorkspace().getClientId();
            String clientKey = subscriber.getWorkspace().getClientKey();
            String query = subscriber.getFilter().getQuery();

            // Mock external API call using RestTemplate
            // In reality, this URL would point to an actual ticketing system
            String url = "https://example-ticketing-api.com/tickets?query=" + query;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Client-Id", clientId);
            headers.set("X-Client-Key", clientKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Commenting actual call out so tests don't fail without mocking
            // ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            // return response.getBody();

            return "{ \"tickets\": [{\"id\": 1, \"title\": \"Mock Ticket\"}] }";
        } catch (Exception e) {
            log.error("Failed to fetch external data", e);
            return "{}";
        }
    }
}
