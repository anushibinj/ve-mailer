package com.anushibinj.veemailer.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Forces every auto-configured RestClient (including the one used by Spring AI)
     * to use the JDK HTTP client instead of Jetty.
     *
     * Root cause: the Octane SDK transitively brings in http2-hpack-9.4.x (Jetty 9)
     * while Spring Boot's BOM upgrades jetty-client to 12.x.  When Jetty 12 initialises
     * its HTTP/2 stack it loads HpackEncoder from the Jetty 9 jar, which references
     * org.eclipse.jetty.util.log.Log (removed in Jetty 10+), causing NoClassDefFoundError.
     * The JDK HTTP client has no Jetty dependency and avoids the conflict entirely.
     */
    @Bean
    public RestClientCustomizer jdkHttpClientCustomizer() {
        return builder -> builder.requestFactory(new JdkClientHttpRequestFactory());
    }
}
