package com.anushibinj.veemailer.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppConfigTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void testRestTemplateBean_IsNotNull() {
        RestTemplate restTemplate = appConfig.restTemplate();
        assertNotNull(restTemplate, "AppConfig.restTemplate() should return a non-null RestTemplate");
    }
}
