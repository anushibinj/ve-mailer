package com.anushibinj.veemailer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anushibinj.veemailer.model.NotificationPreferences;

import jakarta.mail.Session;

@ExtendWith(MockitoExtension.class)
class DynamicMailSenderServiceTest {

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @InjectMocks
    private DynamicMailSenderService dynamicMailSenderService;

    private NotificationPreferences authPrefs() {
        return NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("smtp.example.com")
                .port(587)
                .fromAddress("noreply@example.com")
                .requiresAuth(true)
                .username("user@example.com")
                .password("secret")
                .startTlsEnabled(false)
                .build();
    }

    @Test
    void getFromAddress_ReturnsFromAddressStoredInPrefs() {
        when(notificationPreferencesService.getEntity()).thenReturn(authPrefs());
        assertEquals("noreply@example.com", dynamicMailSenderService.getFromAddress());
    }

    @Test
    void getFromAddress_ThrowsWhenNotConfigured() {
        when(notificationPreferencesService.getEntity()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> dynamicMailSenderService.getFromAddress());
    }

    @Test
    void getSession_WithAuth_SetsAuthTrue() {
        when(notificationPreferencesService.getEntity()).thenReturn(authPrefs());
        Session session = dynamicMailSenderService.getSession();
        assertNotNull(session);
        assertEquals("true", session.getProperty("mail.smtp.auth"));
    }

    @Test
    void getSession_WithoutAuth_SetsAuthFalse() {
        NotificationPreferences relayPrefs = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("relay.internal")
                .port(25)
                .fromAddress("noreply@example.com")
                .requiresAuth(false)
                .build();
        when(notificationPreferencesService.getEntity()).thenReturn(relayPrefs);

        Session session = dynamicMailSenderService.getSession();
        assertNotNull(session);
        assertEquals("false", session.getProperty("mail.smtp.auth"));
    }

    @Test
    void getSession_ThrowsWhenNotConfigured() {
        when(notificationPreferencesService.getEntity()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> dynamicMailSenderService.getSession());
    }
}
