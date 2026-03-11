package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testProcessAndSendNotifications_FormattingAndSending() {
        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setRecipientEmail("user1@example.com");

        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setRecipientEmail("user2@example.com");

        List<EmailSubscriber> subscribers = Arrays.asList(sub1, sub2);
        String mockData = "{\"tickets\": [{\"id\": 1}]}";

        notificationService.processAndSendNotifications(subscribers, mockData);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // Ensure JavaMailSender is called exactly 2 times (once for each subscriber)
        verify(mailSender, times(2)).send(messageCaptor.capture());

        List<SimpleMailMessage> capturedMessages = messageCaptor.getAllValues();

        // Verify formatting and recipient mapping
        assertEquals("user1@example.com", capturedMessages.get(0).getTo()[0]);
        assertTrue(capturedMessages.get(0).getText().contains(mockData));

        assertEquals("user2@example.com", capturedMessages.get(1).getTo()[0]);
        assertTrue(capturedMessages.get(1).getText().contains(mockData));
    }

    @Test
    void testProcessAndSendNotifications_EmptyList_NoEmailSent() {
        notificationService.processAndSendNotifications(Collections.emptyList(), "some data");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_SubjectLineCorrect() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("check@example.com");

        notificationService.processAndSendNotifications(List.of(sub), "data");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        assertEquals("[ve-emailer] Your Notification Digest", captor.getValue().getSubject());
    }

    @Test
    void testProcessAndSendNotifications_BodyContainsPrefixAndData() {
        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("body@example.com");
        String data = "{\"key\": \"value\"}";

        notificationService.processAndSendNotifications(List.of(sub), data);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        String body = captor.getValue().getText();
        assertTrue(body.startsWith("Here is your digest :\n\n"),
                "Body should start with the expected prefix");
        assertTrue(body.contains(data), "Body should contain the raw data");
    }
}
