package com.anushibinj.veemailer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void testSendOtpEmail_SendsCorrectMessage() {
        String recipient = "user@example.com";
        String otp = "654321";

        emailService.sendOtpEmail(recipient, otp);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertNotNull(sent.getTo());
        assertEquals(1, sent.getTo().length);
        assertEquals(recipient, sent.getTo()[0]);
        assertEquals("Your Notification Broker OTP", sent.getSubject());
        assertNotNull(sent.getText());
        assertTrue(sent.getText().contains(otp), "Email body should contain the OTP code");
        assertTrue(sent.getText().contains("expire"), "Email body should mention expiration");
    }

    @Test
    void testSendOtpEmail_MailSenderCalledExactlyOnce() {
        emailService.sendOtpEmail("another@example.com", "111111");

        verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
