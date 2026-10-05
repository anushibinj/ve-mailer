package com.anushibinj.veemailer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailFailureAlertServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @InjectMocks
    private MailFailureAlertService service;

    @Test
    void flush_withNoFailures_sendsNothing() throws Exception {
        service.flush();
        verifyNoInteractions(emailService);
    }

    @Test
    void flush_consolidatesAllFailuresIntoOneEmail() throws Exception {
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(List.of("admin@x.com"));
        service.recordFailure("W1", "F1", "a@x.com", "boom");
        service.recordFailure("W1", "F2", "b@x.com", "boom");
        service.recordFailure("W2", "F3", "c@x.com", "bang");

        service.flush();
        service.flush(); // queue already drained — must not send again

        verify(emailService, times(1)).sendMailFailureAlertToAdmins(argThat(l -> l.size() == 3), eq(List.of("admin@x.com")));
    }

    @Test
    void flush_withoutAdminEmails_doesNotSend() throws Exception {
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(List.of());
        service.recordFailure("W1", "F1", "a@x.com", "boom");
        service.flush();
        verify(emailService, never()).sendMailFailureAlertToAdmins(any(), any());
    }
}
