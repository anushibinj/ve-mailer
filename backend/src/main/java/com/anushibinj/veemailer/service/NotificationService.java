package com.anushibinj.veemailer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.anushibinj.veemailer.model.EmailSubscriber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
	
	// Use the admin email configured in application.properties as the sender address
	@Value("${spring.mail.username}")
	String from;

    private final JavaMailSender mailSender;

    @Async
    public void processAndSendNotifications(List<EmailSubscriber> subscribers, String externalData) {
        for (EmailSubscriber subscriber : subscribers) {
            sendEmail(subscriber.getRecipientEmail(), externalData);
        }
    }

    private void sendEmail(String to, String data) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[ve-emailer] Your Notification Digest");
        message.setText("Here is your digest :\n\n" + data);
        mailSender.send(message);
    }
}
