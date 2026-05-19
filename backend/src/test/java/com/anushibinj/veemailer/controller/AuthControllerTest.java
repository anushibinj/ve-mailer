package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.*;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.AuthService;
import com.anushibinj.veemailer.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signup_Success() throws Exception {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("Password1!")
                .confirmPassword("Password1!")
                .build();

        when(authService.signup(any(SignupRequestDto.class)))
                .thenReturn("OTP has been sent to your email.");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP has been sent to your email."));
    }

    @Test
    void signup_ValidationError() throws Exception {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("")
                .email("invalid")
                .password("")
                .confirmPassword("")
                .build();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_Success() throws Exception {
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@company.com")
                .password("Password1!")
                .build();

        AuthResponseDto response = AuthResponseDto.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponseDto.UserProfileDto.builder()
                        .id("uuid")
                        .name("Test User")
                        .email("test@company.com")
                        .roles(Set.of("MEMBER"))
                        .build())
                .build();

        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("test@company.com"));
    }

    @Test
    void login_InvalidCredentials() throws Exception {
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@company.com")
                .password("wrong")
                .build();

        when(authService.login(any(LoginRequestDto.class)))
                .thenThrow(new IllegalArgumentException("Invalid email or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void verifySignup_Success() throws Exception {
        VerifySignupOtpDto request = VerifySignupOtpDto.builder()
                .email("test@company.com")
                .otp("123456")
                .build();

        AuthResponseDto response = AuthResponseDto.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponseDto.UserProfileDto.builder()
                        .id("uuid")
                        .name("Test User")
                        .email("test@company.com")
                        .roles(Set.of("MEMBER"))
                        .build())
                .build();

        when(authService.verifySignupOtp(any(VerifySignupOtpDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/verify-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void forgotPassword_Success() throws Exception {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .email("test@company.com")
                .build();

        when(authService.forgotPassword(any(ForgotPasswordRequestDto.class)))
                .thenReturn("If an account with this email exists, an OTP has been sent.");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPassword_Success() throws Exception {
        ResetPasswordDto request = ResetPasswordDto.builder()
                .email("test@company.com")
                .otp("123456")
                .newPassword("NewPassword1!")
                .confirmPassword("NewPassword1!")
                .build();

        when(authService.resetPassword(any(ResetPasswordDto.class)))
                .thenReturn("Password has been reset successfully.");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshToken_Success() throws Exception {
        RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken("old-refresh-token")
                .build();

        AuthResponseDto response = AuthResponseDto.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponseDto.UserProfileDto.builder()
                        .id("uuid")
                        .name("Test User")
                        .email("test@company.com")
                        .roles(Set.of("MEMBER"))
                        .build())
                .build();

        when(authService.refreshToken(any(RefreshTokenRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void logout_Success() throws Exception {
        RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken("refresh-token")
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
