package com.anushibinj.veemailer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    @Value("${veemailer.octane.connect-timeout-ms:10000}")
    private long connectTimeoutMs = 10000;

    @Value("${veemailer.octane.read-timeout-ms:60000}")
    private long readTimeoutMs = 60000;

    /** Used throughout the job-run machinery so tests can drive time with a fixed/mutable Clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Built on the JDK HTTP client (see {@link #jdkHttpClientCustomizer()} for why Jetty must be
     * avoided) with explicit connect/read timeouts — without these, a hung ValueEdge connection
     * blocks the poller thread indefinitely instead of throwing, so the retry logic downstream
     * would never even be reached.
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(requestFactory);
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
