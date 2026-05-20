package com.anushibinj.veemailer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;

@SpringBootApplication(exclude = { MailSenderAutoConfiguration.class })
public class NotificationBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationBrokerApplication.class, args);
	}

}
