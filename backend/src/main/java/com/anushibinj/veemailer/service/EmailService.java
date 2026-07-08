package com.anushibinj.veemailer.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final DynamicMailSenderService dynamicMailSenderService;

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
    public void sendInviteEmail(String to, String name, String otp) {
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
                "Your one-time invite code is: " + otp + "\n" +
                "This code will expire in 10 minutes.\n\n" +
                "To set up your account:\n" +
                "  1. Open VE Mailer in your browser.\n" +
                "  2. On the login page, click \"Accept Invite\".\n" +
                "  3. Enter your email address (" + to + ") and the code above.\n" +
                "  4. Choose a new password.\n\n" +
                "If you did not expect this invitation, please ignore this email.",
                "UTF-8"
            );
            message.saveChanges();

            dynamicMailSenderService.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send invite email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send invite email", e);
        }
    }
}
