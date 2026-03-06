package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    @Async
    public void processAndSendNotifications(List<EmailSubscriber> subscribers, String externalData) {
        for (EmailSubscriber subscriber : subscribers) {
            sendEmail(subscriber.getRecipientEmail(), externalData);
        }
    }

    private void sendEmail(String to, String data) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your Notification Digest");
        message.setText("Here is your formatted digest data:\n\n" + data);
        mailSender.send(message);
    }
}
