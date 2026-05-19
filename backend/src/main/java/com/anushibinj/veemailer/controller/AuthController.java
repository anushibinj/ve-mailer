package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.*;
import com.anushibinj.veemailer.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponseWrapper> signup(@Valid @RequestBody SignupRequestDto request) {
        String message = authService.signup(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    @PostMapping("/verify-signup")
    public ResponseEntity<AuthResponseDto> verifySignup(@Valid @RequestBody VerifySignupOtpDto request) {
        AuthResponseDto response = authService.verifySignupOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        AuthResponseDto response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseWrapper> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseWrapper.success("Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseWrapper> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponseWrapper> verifyResetOtp(@Valid @RequestBody VerifyResetOtpDto request) {
        String message = authService.verifyResetOtp(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseWrapper> resetPassword(@Valid @RequestBody ResetPasswordDto request) {
        String message = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto.UserProfileDto> getCurrentUser(Authentication authentication) {
        AuthResponseDto.UserProfileDto profile = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(profile);
    }
}
