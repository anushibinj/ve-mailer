package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "TestSecretKeyForJWTSigningMustBeAtLeast256BitsLongForTests2024!",
                900000L
        );

        Role memberRole = Role.builder().id(UUID.randomUUID()).roleName("MEMBER").build();
        testUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test@company.com")
                .passwordHash("hashed")
                .enabled(true)
                .roles(Set.of(memberRole))
                .build();
    }

    @Test
    void generateAccessToken_ShouldReturnNonNull() {
        String token = jwtService.generateAccessToken(testUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractEmail_ShouldReturnCorrectEmail() {
        String token = jwtService.generateAccessToken(testUser);
        String email = jwtService.extractEmail(token);
        assertEquals("test@company.com", email);
    }

    @Test
    void isTokenValid_ShouldReturnTrueForValidToken() {
        String token = jwtService.generateAccessToken(testUser);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_ShouldReturnFalseForInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.token.here"));
    }

    @Test
    void isTokenValid_ShouldReturnFalseForExpiredToken() {
        // Create a service with 0ms expiration to test expired tokens
        JwtService expiredJwtService = new JwtService(
                "TestSecretKeyForJWTSigningMustBeAtLeast256BitsLongForTests2024!",
                0L
        );
        String token = expiredJwtService.generateAccessToken(testUser);
        assertFalse(expiredJwtService.isTokenValid(token));
    }

    @Test
    void getAccessTokenExpirationMs_ShouldReturnConfiguredValue() {
        assertEquals(900000L, jwtService.getAccessTokenExpirationMs());
    }
}
