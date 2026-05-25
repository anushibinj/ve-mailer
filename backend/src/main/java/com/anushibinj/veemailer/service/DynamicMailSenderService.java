package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.NotificationPreferences;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Provides a Jakarta Mail Session configured from the DB-stored notification preferences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicMailSenderService {

    private final NotificationPreferencesService notificationPreferencesService;

    @Value("${app.bootstrap.admin.email}")
    private String adminEmail;

    /**
     * Builds a fresh Jakarta Mail Session from the current DB preferences.
     * Uses Session.getInstance with an Authenticator — the same pattern used in other
     * projects — so that Transport.send() handles the SMTP AUTH handshake correctly.
     *
     * @throws IllegalStateException if notification preferences are not configured yet
     */
    public Session getSession() {
        NotificationPreferences prefs = notificationPreferencesService.getEntity();
        if (prefs == null) {
            throw new IllegalStateException("Notification preferences are not configured. "
                    + "Please configure SMTP settings in the Admin Control Panel.");
        }

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", prefs.getHost());
        props.put("mail.smtp.port", String.valueOf(prefs.getPort()));
        props.put("mail.smtp.auth", "true");
        if (prefs.isStartTlsEnabled()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        String username = prefs.getUsername();
        String password = prefs.getPassword();

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    /**
     * Sends a MimeMessage via Transport.send(). Extracted as an instance method so it
     * can be mocked in unit tests without requiring a live SMTP connection.
     * Wraps MessagingException in RuntimeException so callers and tests need not
     * declare a checked exception.
     */
    public void send(MimeMessage message) {
        try {
            Transport.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Returns the sender "from" address — always the configured bootstrap admin email.
     */
    public String getFromAddress() {
        return adminEmail;
    }
}
