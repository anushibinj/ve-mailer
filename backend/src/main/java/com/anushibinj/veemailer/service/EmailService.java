package com.anushibinj.veemailer.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final DynamicMailSenderService dynamicMailSenderService;
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("[ve-emailer] Your ve-emailer OTP", "UTF-8");
            message.setText("Your OTP code is: " + otp + "\nThis code will expire in 10 minutes.", "UTF-8");
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    @Async
    public void sendInviteMagicLinkEmail(String to, String name, String magicLink, int expiresInMinutes) {
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("[ve-mailer] You've been invited to VE Mailer", "UTF-8");
            message.setText(
                "Hello " + name + ",\n\n" +
                "You have been invited to use VE Mailer.\n\n" +
                "Use the secure one-time link below to finish setting up your account:\n\n" +
                magicLink + "\n\n" +
                "This link will expire in " + expiresInMinutes + " minutes and can be used only once.\n\n" +
                "To set up your account:\n" +
                "  1. Click the link above.\n" +
                "  2. Choose a new password.\n\n" +
                "You can also request a fresh link from: " + frontendUrl + "/accept-invite\n\n" +
                "If you did not expect this invitation, please ignore this email.",
                "UTF-8"
            );
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send invite magic link email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send invite magic link email", e);
        }
    }

    @Async
    public void sendOnboardingNotificationToAdmins(String newUserName, String newUserEmail, List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }
        try {
            Session session = dynamicMailSenderService.getSession();
            String from = dynamicMailSenderService.getFromAddress();
            String recipientList = String.join(",", adminEmails);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientList));
            message.setSubject("[ve-mailer] New user onboarded: " + newUserName, "UTF-8");
            message.setText(
                "Hello,\n\n" +
                "A new user has completed onboarding on VE Mailer.\n\n" +
                "  Name:  " + newUserName + "\n" +
                "  Email: " + newUserEmail + "\n\n" +
                "You can manage users at: " + frontendUrl + "/admin\n\n" +
                "This is an automated system notification.",
                "UTF-8"
            );
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send onboarding notification for user {} to admins: {}", newUserEmail, e.getMessage());
            // Non-critical — do not rethrow; user onboarding should not fail because of this
        }
    }
}
