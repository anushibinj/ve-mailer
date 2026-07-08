package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.*;
import com.anushibinj.veemailer.model.*;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.auth.allowed-domains}")
    private String allowedDomains;

    @Transactional
    public String signup(SignupRequestDto request) {
        // Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        // Validate email domain
        validateEmailDomain(request.getEmail());

        // Check for duplicate email
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        // Validate password strength
        validatePasswordStrength(request.getPassword());

        // Store pending user data in OTP payload (JSON)
        String payload = String.format(
                "{\"name\":\"%s\",\"email\":\"%s\",\"passwordHash\":\"%s\"}",
                request.getName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword())
        );

        // Send OTP for verification
        otpService.createAndSendOtp(request.getEmail(), ActionType.SIGNUP_VERIFICATION, payload);

        return "OTP has been sent to your email. Please verify to complete registration.";
    }

    @Transactional
    public AuthResponseDto verifySignupOtp(VerifySignupOtpDto request) {
        // Validate OTP
        OtpRequest otpRequest = otpService.validateOtp(request.getEmail(), request.getOtp());

        if (otpRequest.getActionType() != ActionType.SIGNUP_VERIFICATION) {
            throw new IllegalArgumentException("Invalid OTP purpose.");
        }

        // Parse payload to create user
        String payload = otpRequest.getPayload();
        String name = extractJsonValue(payload, "name");
        String email = extractJsonValue(payload, "email");
        String passwordHash = extractJsonValue(payload, "passwordHash");

        // Get or create MEMBER role
        Role memberRole = roleRepository.findByRoleName("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("MEMBER").build()));

        // Create user
        AppUser user = AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordHash)
                .enabled(true)
                .roles(Set.of(memberRole))
                .build();

        user = appUserRepository.save(user);

        // Cleanup OTP
        otpService.cleanupOtp(otpRequest);

        // Auto-login: generate tokens
        return buildAuthResponse(user);
    }

    public AuthResponseDto login(LoginRequestDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is disabled.");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponseDto refreshToken(RefreshTokenRequestDto request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));

        if (refreshTokenService.isTokenExpired(refreshToken)) {
            refreshTokenService.revokeToken(refreshToken);
            throw new IllegalArgumentException("Refresh token has expired. Please login again.");
        }

        AppUser user = refreshToken.getUser();

        // Revoke old refresh token and issue new one
        refreshTokenService.revokeToken(refreshToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenService::revokeToken);
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequestDto request) {
        // Check user exists
        if (!appUserRepository.existsByEmail(request.getEmail())) {
            // Don't reveal if account exists for security
            return "If an account with this email exists, an OTP has been sent.";
        }

        otpService.createAndSendOtp(request.getEmail(), ActionType.PASSWORD_RESET, null);

        return "If an account with this email exists, an OTP has been sent.";
    }

    @Transactional
    public String verifyResetOtp(VerifyResetOtpDto request) {
        OtpRequest otpRequest = otpService.validateOtp(request.getEmail(), request.getOtp());

        if (otpRequest.getActionType() != ActionType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Invalid OTP purpose.");
        }

        // OTP is valid, return success (do not cleanup yet - will be done in resetPassword)
        return "OTP verified successfully. You may now reset your password.";
    }

    @Transactional
    public String resetPassword(ResetPasswordDto request) {
        // Validate OTP again (ensures it's still valid)
        OtpRequest otpRequest = otpService.validateOtp(request.getEmail(), request.getOtp());

        if (otpRequest.getActionType() != ActionType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Invalid OTP purpose.");
        }

        // Validate password confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        // Validate password strength
        validatePasswordStrength(request.getNewPassword());

        // Update password
        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // If the user was invited but used forgot-password as a fallback, clear the flag
        user.setMustSetPassword(false);
        appUserRepository.save(user);

        // Invalidate all existing sessions
        refreshTokenService.revokeAllUserTokens(user);

        // Cleanup OTP
        otpService.cleanupOtp(otpRequest);

        return "Password has been reset successfully. Please login with your new password.";
    }

    /**
     * Creates a new user account without requiring self-signup.
     * Sends an invite OTP so the user can set their own password via /accept-invite.
     */
    @Transactional
    public String onboardUser(AdminOnboardUserRequestDto request) {
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A user with this email is already registered.");
        }

        Role memberRole = roleRepository.findByRoleName("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("MEMBER").build()));

        // Random temp password — the user will never know it; they must use the invite OTP flow
        String tempPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

        AppUser user = AppUser.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(tempPasswordHash)
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();

        appUserRepository.save(user);

        otpService.createAndSendInviteOtp(request.getEmail(), request.getName());

        return "User onboarded successfully. An invite has been sent to " + request.getEmail() + ".";
    }

    /**
     * Validates an invite OTP, sets the user's password, and auto-logs them in.
     */
    @Transactional
    public AuthResponseDto acceptInvite(AcceptInviteRequestDto request) {
        OtpRequest otpRequest = otpService.validateOtp(request.getEmail(), request.getOtp());

        if (otpRequest.getActionType() != ActionType.INVITE) {
            throw new IllegalArgumentException("Invalid OTP purpose.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        validatePasswordStrength(request.getNewPassword());

        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!user.isMustSetPassword()) {
            throw new IllegalArgumentException("This invite has already been accepted.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustSetPassword(false);
        appUserRepository.save(user);

        otpService.cleanupOtp(otpRequest);

        return buildAuthResponse(user);
    }

    /**
     * Resends an invite OTP to a pending (mustSetPassword=true) user.
     */
    @Transactional
    public String resendInvite(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email."));

        if (!user.isMustSetPassword()) {
            throw new IllegalArgumentException("This account has already been activated.");
        }

        otpService.createAndSendInviteOtp(email, user.getName());

        return "A new invite code has been sent to " + email + ".";
    }

    public AuthResponseDto.UserProfileDto getCurrentUser(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        return AuthResponseDto.UserProfileDto.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toSet()))
                .mustSetPassword(user.isMustSetPassword())
                .build();
    }

    private AuthResponseDto buildAuthResponse(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponseDto.UserProfileDto.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .email(user.getEmail())
                        .roles(user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toSet()))
                        .mustSetPassword(user.isMustSetPassword())
                        .build())
                .build();
    }

    private void validateEmailDomain(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        List<String> allowed = Arrays.stream(allowedDomains.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (!allowed.contains(domain)) {
            throw new IllegalArgumentException(
                    "Email domain '" + domain + "' is not allowed. Allowed domains: " + String.join(", ", allowed));
        }
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one digit.");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character.");
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey) + searchKey.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
