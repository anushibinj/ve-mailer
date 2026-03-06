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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
