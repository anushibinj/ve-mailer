package com.anushibinj.veemailer.config;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebConfigTest {

    private final WebConfig webConfig = new WebConfig();

    @Test
    void testAddCorsMappings_RegistersAllOriginsAndMethods() {
        // CorsRegistry.addMapping() returns a CorsRegistration with fluent builder methods.
        // Use RETURNS_SELF so the fluent chain never returns null.
        CorsRegistration registration = mock(CorsRegistration.class, Answers.RETURNS_SELF);
        CorsRegistry registry = mock(CorsRegistry.class);

        when(registry.addMapping("/**")).thenReturn(registration);

        webConfig.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOrigins(
                "http://localhost:5173",
                "http://localhost:80",
                "http://localhost");
        verify(registration).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        verify(registration).allowedHeaders("*");
        verify(registration).allowCredentials(true);
    }
}
