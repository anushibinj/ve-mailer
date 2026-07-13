package com.anushibinj.veemailer.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private DynamicMailSenderService dynamicMailSenderService;

    @InjectMocks
    private EmailService emailService;

    private Session testSession() {
        return Session.getInstance(new Properties());
    }

    @Test
    void testSendOtpEmail_SendsCorrectMessage() throws Exception {
        String recipient = "user@example.com";
        String otp = "654321";

        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendOtpEmail(recipient, otp);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(dynamicMailSenderService, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertNotNull(sent.getRecipients(Message.RecipientType.TO));
        assertEquals(1, sent.getRecipients(Message.RecipientType.TO).length);
        assertEquals(recipient, sent.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("[ve-emailer] Your ve-emailer OTP", sent.getSubject());
        String body = sent.getContent().toString();
        assertTrue(body.contains(otp), "Email body should contain the OTP code");
        assertTrue(body.contains("expire"), "Email body should mention expiration");
    }

    @Test
    void testSendOtpEmail_MailSenderCalledExactlyOnce() throws Exception {
        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendOtpEmail("another@example.com", "111111");

        verify(dynamicMailSenderService, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendInviteEmail_IncludesFrontendUrlLink() throws Exception {
        String recipient = "invitee@example.com";
        String otp = "123456";
        String frontendUrl = "http://localhost:5173";

        ReflectionTestUtils.setField(emailService, "frontendUrl", frontendUrl);
        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendInviteEmail(recipient, "Invitee", otp);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(dynamicMailSenderService, times(1)).send(captor.capture());
        String body = captor.getValue().getContent().toString();

        assertTrue(body.contains("Open VE Mailer in your browser: " + frontendUrl),
                "Invite email body should include the VE Mailer frontend URL");
        assertTrue(body.contains(otp), "Invite email body should include the OTP code");
    }
}
