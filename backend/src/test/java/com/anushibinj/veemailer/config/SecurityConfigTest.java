package com.anushibinj.veemailer.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void testPasswordEncoder_ReturnsBCryptEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder,
                "PasswordEncoder should be a BCryptPasswordEncoder");
    }

    @Test
    void testPasswordEncoder_EncodesAndMatchesCorrectly() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String rawPassword = "TestPassword123";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoder.matches(rawPassword, encoded),
                "Encoded password should match original raw password");
    }
}
