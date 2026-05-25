package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.NotificationPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Provides a JavaMailSender configured from the DB-stored notification preferences.
 */
@Service
@RequiredArgsConstructor
public class DynamicMailSenderService {

    private final NotificationPreferencesService notificationPreferencesService;

    @Value("${app.bootstrap.admin.email}")
    private String adminEmail;

    /**
     * Builds a fresh JavaMailSender from the current DB preferences.
     *
     * @throws IllegalStateException if notification preferences are not configured yet
     */
    public JavaMailSender getMailSender() {
        NotificationPreferences prefs = notificationPreferencesService.getEntity();
        if (prefs == null) {
            throw new IllegalStateException("Notification preferences are not configured. "
                    + "Please configure SMTP settings in the Admin Control Panel.");
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(prefs.getHost());
        mailSender.setPort(prefs.getPort());
        mailSender.setUsername(prefs.getUsername());
        mailSender.setPassword(prefs.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        if (prefs.isStartTlsEnabled()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        return mailSender;
    }

    /**
     * Returns the sender "from" address — always the configured bootstrap admin email.
     */
    public String getFromAddress() {
        return adminEmail;
    }
}
