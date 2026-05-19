package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.*;
import com.anushibinj.veemailer.model.*;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpService otpService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private Role memberRole;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "allowedDomains", "company.com,int-company.com");

        memberRole = Role.builder().id(UUID.randomUUID()).roleName("MEMBER").build();
        testUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test@company.com")
                .passwordHash("hashed-password")
                .enabled(true)
                .roles(Set.of(memberRole))
                .build();
    }

    @Test
    void signup_Success() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("Password1!")
                .confirmPassword("Password1!")
                .build();

        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        String result = authService.signup(request);

        assertNotNull(result);
        assertTrue(result.contains("OTP has been sent"));
        verify(otpService).createAndSendOtp(eq("test@company.com"), eq(ActionType.SIGNUP_VERIFICATION), anyString());
    }

    @Test
    void signup_InvalidDomain() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@invalid.com")
                .password("Password1!")
                .confirmPassword("Password1!")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void signup_DuplicateEmail() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("Password1!")
                .confirmPassword("Password1!")
                .build();

        when(appUserRepository.existsByEmail("test@company.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void signup_PasswordMismatch() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("Password1!")
                .confirmPassword("DifferentPassword1!")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void signup_WeakPassword() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("weak")
                .confirmPassword("weak")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void login_Success() {
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@company.com")
                .password("Password1!")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@company.com", null));
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(testUser))
                .thenReturn(RefreshToken.builder().token("refresh-token").build());

        AuthResponseDto result = authService.login(request);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals("Bearer", result.getTokenType());
    }

    @Test
    void login_InvalidCredentials() {
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@company.com")
                .password("WrongPassword1!")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void login_DisabledAccount() {
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@company.com")
                .password("Password1!")
                .build();

        testUser.setEnabled(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@company.com", null));
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void forgotPassword_UserExists() {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .email("test@company.com")
                .build();

        when(appUserRepository.existsByEmail("test@company.com")).thenReturn(true);

        String result = authService.forgotPassword(request);

        assertNotNull(result);
        verify(otpService).createAndSendOtp(eq("test@company.com"), eq(ActionType.PASSWORD_RESET), isNull());
    }

    @Test
    void forgotPassword_UserNotExists() {
        ForgotPasswordRequestDto request = ForgotPasswordRequestDto.builder()
                .email("nonexistent@company.com")
                .build();

        when(appUserRepository.existsByEmail("nonexistent@company.com")).thenReturn(false);

        String result = authService.forgotPassword(request);

        // Should not reveal if account exists
        assertNotNull(result);
        verify(otpService, never()).createAndSendOtp(anyString(), any(), any());
    }

    @Test
    void resetPassword_Success() {
        ResetPasswordDto request = ResetPasswordDto.builder()
                .email("test@company.com")
                .otp("123456")
                .newPassword("NewPassword1!")
                .confirmPassword("NewPassword1!")
                .build();

        OtpRequest otpRequest = OtpRequest.builder()
                .email("test@company.com")
                .actionType(ActionType.PASSWORD_RESET)
                .build();

        when(otpService.validateOtp("test@company.com", "123456")).thenReturn(otpRequest);
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hashed");

        String result = authService.resetPassword(request);

        assertNotNull(result);
        assertTrue(result.contains("reset successfully"));
        verify(refreshTokenService).revokeAllUserTokens(testUser);
        verify(otpService).cleanupOtp(otpRequest);
    }

    @Test
    void logout_Success() {
        RefreshToken token = RefreshToken.builder().token("refresh-token").build();
        when(refreshTokenService.findByToken("refresh-token")).thenReturn(Optional.of(token));

        authService.logout("refresh-token");

        verify(refreshTokenService).revokeToken(token);
    }

    @Test
    void getCurrentUser_Success() {
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        AuthResponseDto.UserProfileDto profile = authService.getCurrentUser("test@company.com");

        assertNotNull(profile);
        assertEquals("Test User", profile.getName());
        assertEquals("test@company.com", profile.getEmail());
        assertTrue(profile.getRoles().contains("MEMBER"));
    }

    @Test
    void verifySignupOtp_Success() {
        VerifySignupOtpDto request = VerifySignupOtpDto.builder()
                .email("test@company.com")
                .otp("123456")
                .build();

        OtpRequest otpRequest = OtpRequest.builder()
                .email("test@company.com")
                .actionType(ActionType.SIGNUP_VERIFICATION)
                .payload("{\"name\":\"Test User\",\"email\":\"test@company.com\",\"passwordHash\":\"hashed\"}")
                .build();

        when(otpService.validateOtp("test@company.com", "123456")).thenReturn(otpRequest);
        when(roleRepository.findByRoleName("MEMBER")).thenReturn(Optional.of(memberRole));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwtService.generateAccessToken(any(AppUser.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(any(AppUser.class)))
                .thenReturn(RefreshToken.builder().token("refresh-token").build());

        AuthResponseDto result = authService.verifySignupOtp(request);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        verify(otpService).cleanupOtp(otpRequest);
    }

    @Test
    void refreshToken_Success() {
        RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken("old-refresh-token")
                .build();

        RefreshToken oldToken = RefreshToken.builder()
                .token("old-refresh-token")
                .user(testUser)
                .expiresAt(java.time.LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.findByToken("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(refreshTokenService.isTokenExpired(oldToken)).thenReturn(false);
        when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(testUser))
                .thenReturn(RefreshToken.builder().token("new-refresh-token").build());

        AuthResponseDto result = authService.refreshToken(request);

        assertNotNull(result);
        assertEquals("new-access-token", result.getAccessToken());
        verify(refreshTokenService).revokeToken(oldToken);
    }

    @Test
    void refreshToken_Expired() {
        RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken("expired-token")
                .build();

        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired-token")
                .user(testUser)
                .expiresAt(java.time.LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenService.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));
        when(refreshTokenService.isTokenExpired(expiredToken)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.refreshToken(request));
    }
}
