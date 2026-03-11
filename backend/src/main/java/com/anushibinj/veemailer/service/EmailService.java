package com.anushibinj.veemailer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {
	
	// Use the admin email configured in application.properties as the sender address
	@Value("${spring.mail.username}")
	String from;

    private final JavaMailSender mailSender;

    @Async
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[ve-emailer] Your ve-emailer OTP");
        message.setText("Your OTP code is: " + otp + "\nThis code will expire in 10 minutes.");
        mailSender.send(message);
    }
}
