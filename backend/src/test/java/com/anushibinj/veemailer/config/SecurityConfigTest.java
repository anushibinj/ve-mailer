package com.anushibinj.veemailer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Auth401EntryPoint auth401EntryPoint;

    @Autowired
    private Auth403AccessDeniedHandler auth403AccessDeniedHandler;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testPasswordEncoder_ReturnsBCryptEncoder() {
        assertNotNull(passwordEncoder);
        assertTrue(passwordEncoder instanceof BCryptPasswordEncoder,
                "PasswordEncoder should be a BCryptPasswordEncoder");
    }

    @Test
    void testPasswordEncoder_EncodesAndMatchesCorrectly() {
        String rawPassword = "TestPassword123";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(passwordEncoder.matches(rawPassword, encoded),
                "Encoded password should match original raw password");
    }

    @Test
    void testAuth401EntryPoint_IsBeanWired() {
        assertNotNull(auth401EntryPoint, "Auth401EntryPoint bean should be present in the context");
    }

    @Test
    void testAuth403AccessDeniedHandler_IsBeanWired() {
        assertNotNull(auth403AccessDeniedHandler, "Auth403AccessDeniedHandler bean should be present in the context");
    }

    @Test
    void unauthenticated_ProtectedEndpoint_Returns401Json() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/workspaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void invalidJwt_ProtectedEndpoint_Returns401Json() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/workspaces")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void publicEndpoint_IsAccessibleWithoutToken() throws Exception {
        // Public auth endpoints must remain accessible without credentials
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"noone@company.com\",\"password\":\"x\"}"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() != 401,
                        "Public auth endpoint must not return 401"));
    }
}

