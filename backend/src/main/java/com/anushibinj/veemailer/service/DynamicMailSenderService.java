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

    private NotificationPreferences getPrefs() {
        NotificationPreferences prefs = notificationPreferencesService.getEntity();
        if (prefs == null) {
            throw new IllegalStateException("Notification preferences are not configured. "
                    + "Please configure SMTP settings in the Admin Control Panel.");
        }
        return prefs;
    }

    public Session getSession() {
        NotificationPreferences prefs = getPrefs();

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", prefs.getHost());
        props.put("mail.smtp.port", String.valueOf(prefs.getPort()));

        if (prefs.isRequiresAuth()) {
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
        } else {
            props.put("mail.smtp.auth", "false");
            return Session.getInstance(props);
        }
    }

    public void send(MimeMessage message) {
        try {
            Transport.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public String getFromAddress() {
        return getPrefs().getFromAddress();
    }
}
