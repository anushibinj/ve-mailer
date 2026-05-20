package com.anushibinj.veemailer.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {
	
    private final DynamicMailSenderService dynamicMailSenderService;

    @Async
    public void sendOtpEmail(String to, String otp) {
        JavaMailSender mailSender = dynamicMailSenderService.getMailSender();
        String from = dynamicMailSenderService.getFromAddress();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[ve-emailer] Your ve-emailer OTP");
        message.setText("Your OTP code is: " + otp + "\nThis code will expire in 10 minutes.");
        mailSender.send(message);
    }
}
