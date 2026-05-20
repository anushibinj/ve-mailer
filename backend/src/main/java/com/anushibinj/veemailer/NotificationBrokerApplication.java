package com.anushibinj.veemailer;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;

@SpringBootApplication(exclude = {
        MailSenderAutoConfiguration.class,
        // Spring AI OpenAI auto-configuration is excluded because AI settings are
        // managed through the Admin Control Panel and loaded from the database at
        // runtime via DynamicAiClientService. Static spring.ai.openai.* properties
        // are no longer used.
        OpenAiAutoConfiguration.class
})
public class NotificationBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationBrokerApplication.class, args);
	}

}
