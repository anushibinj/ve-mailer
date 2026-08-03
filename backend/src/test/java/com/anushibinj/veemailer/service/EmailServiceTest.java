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
        assertEquals("[ve-mailer] Your sign-in code", sent.getSubject());
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
    void testSendInviteMagicLinkEmail_IncludesMagicLinkAndFallbackPage() throws Exception {
        String recipient = "invitee@example.com";
        String frontendUrl = "http://localhost:5173";
        String magicLink = frontendUrl + "/accept-invite?token=abc";

        ReflectionTestUtils.setField(emailService, "frontendUrl", frontendUrl);
        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendInviteMagicLinkEmail(recipient, "Invitee", magicLink, 10);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(dynamicMailSenderService, times(1)).send(captor.capture());
        String body = captor.getValue().getContent().toString();

        assertTrue(body.contains(magicLink), "Invite email body should include the magic link");
        assertTrue(body.contains(frontendUrl + "/accept-invite"),
                "Invite email body should include the fallback accept-invite page URL");
        assertTrue(body.contains("only once"), "Invite email body should mention single-use");
    }

    @Test
    void testSendRoleChangeNotification_WithoutWorkspace_OmitsWorkspaceRowAndLink() throws Exception {
        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendRoleChangeNotification("Jane Doe", "jane@example.com", "MEMBER", "WORKSPACE_ADMIN", null, null);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(dynamicMailSenderService, times(1)).send(captor.capture());
        String body = captor.getValue().getContent().toString();

        assertTrue(body.contains("MEMBER"), "Body should mention the previous role");
        assertTrue(body.contains("WORKSPACE_ADMIN"), "Body should mention the new role");
        assertFalse(body.contains("Workspace</td>"), "Body should not include a Workspace row when no workspace is given");
        assertFalse(body.contains("/workspace/"), "Body should not include a workspace link when no workspace is given");
    }

    @Test
    void testSendRoleChangeNotification_WithWorkspace_IncludesWorkspaceNameAndLink() throws Exception {
        String frontendUrl = "http://localhost:5173";
        String workspaceId = "d25d5478-7d5d-404d-ad5e-62267350aecc";

        ReflectionTestUtils.setField(emailService, "frontendUrl", frontendUrl);
        when(dynamicMailSenderService.getSession()).thenReturn(testSession());
        when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        doNothing().when(dynamicMailSenderService).send(any(MimeMessage.class));

        emailService.sendRoleChangeNotification(
                "Jane Doe", "jane@example.com", "MEMBER", "WORKSPACE_ADMIN", workspaceId, "Portfolio-Hyd");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(dynamicMailSenderService, times(1)).send(captor.capture());
        String body = captor.getValue().getContent().toString();

        assertTrue(body.contains("Portfolio-Hyd"), "Body should mention the workspace title");
        assertTrue(body.contains(frontendUrl + "/workspace/" + workspaceId),
                "Body should include a link to the workspace");
    }
}
