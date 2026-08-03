package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.*;
import com.anushibinj.veemailer.model.*;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.InviteMagicLinkRepository;
import com.anushibinj.veemailer.repository.OtpRequestRepository;
import com.anushibinj.veemailer.repository.RoleRepository;
import com.anushibinj.veemailer.repository.WorkspaceAdminRepository;
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
import java.util.List;
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
    private InviteMagicLinkService inviteMagicLinkService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private WorkspaceAdminRepository workspaceAdminRepository;

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @Mock
    private OtpRequestRepository otpRequestRepository;

    @Mock
    private InviteMagicLinkRepository inviteMagicLinkRepository;

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @Mock
    private EmailService emailService;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private AuthService authService;

    private Role memberRole;
    private Role workspaceAdminRole;
    private Role globalAdminRole;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "allowedDomains", "company.com,int-company.com");

        memberRole = Role.builder().id(UUID.randomUUID()).roleName("MEMBER").build();
        workspaceAdminRole = Role.builder().id(UUID.randomUUID()).roleName("WORKSPACE_ADMIN").build();
        globalAdminRole = Role.builder().id(UUID.randomUUID()).roleName("ADMIN").build();
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

        when(appUserRepository.findByEmail(anyString())).thenReturn(Optional.empty());
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

        // A fully-onboarded existing user (mustSetPassword=false) should be rejected as a duplicate.
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void signup_AllowedForInvitedUserWhoHasNotSetPasswordYet() {
        SignupRequestDto request = SignupRequestDto.builder()
                .name("Test User")
                .email("test@company.com")
                .password("Password1!")
                .confirmPassword("Password1!")
                .build();

        AppUser invitedUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test@company.com")
                .passwordHash("random-temp-hash")
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();

        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(invitedUser));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        String result = authService.signup(request);

        assertNotNull(result);
        assertTrue(result.contains("OTP has been sent"));
        verify(otpService).createAndSendOtp(eq("test@company.com"), eq(ActionType.SIGNUP_VERIFICATION), anyString());
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
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(List.of("admin@company.com"));
        when(jwtService.generateAccessToken(any(AppUser.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(any(AppUser.class)))
                .thenReturn(RefreshToken.builder().token("refresh-token").build());

        AuthResponseDto result = authService.verifySignupOtp(request);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        verify(otpService).cleanupOtp(otpRequest);
        verify(emailService).sendOnboardingNotificationToAdmins(eq("Test User"), eq("test@company.com"), eq(List.of("admin@company.com")));
    }

    @Test
    void verifySignupOtp_CompletesOnboardingForPreviouslyInvitedUser() {
        VerifySignupOtpDto request = VerifySignupOtpDto.builder()
                .email("test@company.com")
                .otp("123456")
                .build();

        OtpRequest otpRequest = OtpRequest.builder()
                .email("test@company.com")
                .actionType(ActionType.SIGNUP_VERIFICATION)
                .payload("{\"name\":\"Test User\",\"email\":\"test@company.com\",\"passwordHash\":\"hashed\"}")
                .build();

        AppUser invitedUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Old Name")
                .email("test@company.com")
                .passwordHash("random-temp-hash")
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();

        when(otpService.validateOtp("test@company.com", "123456")).thenReturn(otpRequest);
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(invitedUser));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(List.of("admin@company.com"));
        when(jwtService.generateAccessToken(any(AppUser.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(any(AppUser.class)))
                .thenReturn(RefreshToken.builder().token("refresh-token").build());

        AuthResponseDto result = authService.verifySignupOtp(request);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        assertEquals("Test User", invitedUser.getName());
        assertEquals("hashed", invitedUser.getPasswordHash());
        assertFalse(invitedUser.isMustSetPassword());
        verify(appUserRepository, never()).save(argThat(u -> u != invitedUser));
        verify(otpService).cleanupOtp(otpRequest);
    }

    @Test
    void verifySignupOtp_RejectsWhenUserAlreadyOnboardedByAnotherPath() {
        VerifySignupOtpDto request = VerifySignupOtpDto.builder()
                .email("test@company.com")
                .otp("123456")
                .build();

        OtpRequest otpRequest = OtpRequest.builder()
                .email("test@company.com")
                .actionType(ActionType.SIGNUP_VERIFICATION)
                .payload("{\"name\":\"Test User\",\"email\":\"test@company.com\",\"passwordHash\":\"hashed\"}")
                .build();

        // Already fully onboarded (e.g. accepted the invite magic link in the meantime).
        when(otpService.validateOtp("test@company.com", "123456")).thenReturn(otpRequest);
        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class, () -> authService.verifySignupOtp(request));
        verify(appUserRepository, never()).save(any(AppUser.class));
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

    @Test
    void requestInviteMagicLink_PendingUser_SendsLink() {
        AppUser pendingUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Pending User")
                .email("pending@company.com")
                .passwordHash("hashed")
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();

        when(appUserRepository.findByEmail("pending@company.com")).thenReturn(Optional.of(pendingUser));

        String result = authService.requestInviteMagicLink("pending@company.com");

        assertEquals("If your invite is still pending, a magic link has been sent to your email.", result);
        verify(inviteMagicLinkService).createAndSendInviteMagicLink("pending@company.com", "Pending User");
    }

    @Test
    void requestInviteMagicLink_NonPendingUser_ReturnsGenericMessage() {
        when(appUserRepository.findByEmail("active@company.com")).thenReturn(Optional.of(testUser));

        String result = authService.requestInviteMagicLink("active@company.com");

        assertEquals("If your invite is still pending, a magic link has been sent to your email.", result);
        verify(inviteMagicLinkService, never()).createAndSendInviteMagicLink(anyString(), anyString());
    }

    @Test
    void resendPendingInviteByAdmin_PendingUser_SendsLink() {
        AppUser pendingUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Pending User")
                .email("pending@company.com")
                .passwordHash("hashed")
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();
        when(appUserRepository.findById(pendingUser.getId())).thenReturn(Optional.of(pendingUser));

        String result = authService.resendPendingInviteByAdmin("admin@company.com", pendingUser.getId());

        assertEquals("Invite resent to pending@company.com.", result);
        verify(inviteMagicLinkService).createAndSendInviteMagicLink("pending@company.com", "Pending User");
    }

    @Test
    void resendPendingInviteByAdmin_ActiveUser_Throws() {
        AppUser activeUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Active User")
                .email("active@company.com")
                .passwordHash("hashed")
                .enabled(true)
                .mustSetPassword(false)
                .roles(Set.of(memberRole))
                .build();
        when(appUserRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.resendPendingInviteByAdmin("admin@company.com", activeUser.getId()));
        assertTrue(ex.getMessage().contains("already completed onboarding"));
        verify(inviteMagicLinkService, never()).createAndSendInviteMagicLink(anyString(), anyString());
    }

    @Test
    void acceptInvite_UsesMagicLinkToken() {
        AcceptInviteRequestDto request = AcceptInviteRequestDto.builder()
                .token("magic-token")
                .newPassword("NewPassword1!")
                .confirmPassword("NewPassword1!")
                .build();
        AppUser pendingUser = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Pending User")
                .email("pending@company.com")
                .passwordHash("hashed")
                .enabled(true)
                .mustSetPassword(true)
                .roles(Set.of(memberRole))
                .build();

        when(inviteMagicLinkService.consumeInviteMagicLink("magic-token")).thenReturn("pending@company.com");
        when(appUserRepository.findByEmail("pending@company.com")).thenReturn(Optional.of(pendingUser));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hashed");
        when(jwtService.generateAccessToken(pendingUser)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenService.createRefreshToken(pendingUser))
                .thenReturn(RefreshToken.builder().token("refresh-token").build());
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(java.util.Collections.emptyList());

        AuthResponseDto result = authService.acceptInvite(request);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        verify(refreshTokenService).revokeAllUserTokens(pendingUser);
    }

    @Test
    void deleteUserBySuperAdmin_Success_CleansRelatedData() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Delete Me")
                .email("delete.me@company.com")
                .passwordHash("hash")
                .enabled(true)
                .mustSetPassword(false)
                .roles(Set.of(memberRole))
                .build();

        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));

        String result = authService.deleteUserBySuperAdmin("admin@company.com", target.getId());

        assertTrue(result.contains("deleted successfully"));
        verify(refreshTokenService).deleteAllUserTokens(target.getId());
        verify(workspaceAdminRepository).deleteByUser_Id(target.getId());
        verify(emailSubscriberRepository).deleteByRecipientEmail("delete.me@company.com");
        verify(otpRequestRepository).deleteByEmail("delete.me@company.com");
        verify(inviteMagicLinkRepository).deleteByEmail("delete.me@company.com");
        verify(appUserRepository).deleteUserRoleMappings(target.getId());
        verify(appUserRepository).delete(target);
    }

    @Test
    void deleteUserBySuperAdmin_RejectsSelfDelete() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .email("admin@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(Set.of(memberRole))
                .build();
        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.deleteUserBySuperAdmin("admin@company.com", target.getId()));
        assertTrue(ex.getMessage().contains("cannot delete your own"));
    }

    @Test
    void updateGlobalRole_PromotesMemberToWorkspaceAdmin() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Plain User")
                .email("plain.user@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new java.util.HashSet<>(Set.of(memberRole)))
                .build();
        UserSummaryDto expectedSummary = UserSummaryDto.builder().id(target.getId()).build();

        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleName("WORKSPACE_ADMIN")).thenReturn(Optional.of(workspaceAdminRole));
        when(userQueryService.getUserSummary(target.getId())).thenReturn(expectedSummary);

        UserSummaryDto result = authService.updateGlobalRole("admin@company.com", target.getId(), "WORKSPACE_ADMIN");

        assertEquals(expectedSummary, result);
        assertTrue(target.getRoles().contains(workspaceAdminRole));
        verify(appUserRepository).save(target);
        verify(emailService).sendRoleChangeNotification("Plain User", "plain.user@company.com", "MEMBER", "WORKSPACE_ADMIN", null, null);
    }

    @Test
    void updateGlobalRole_DemotesWorkspaceAdminToMember_WithoutTouchingWorkspaceMappings() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Workspace Admin")
                .email("ws.admin@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new java.util.HashSet<>(Set.of(memberRole, workspaceAdminRole)))
                .build();
        UserSummaryDto expectedSummary = UserSummaryDto.builder().id(target.getId()).build();

        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userQueryService.getUserSummary(target.getId())).thenReturn(expectedSummary);

        UserSummaryDto result = authService.updateGlobalRole("admin@company.com", target.getId(), "MEMBER");

        assertEquals(expectedSummary, result);
        assertFalse(target.getRoles().contains(workspaceAdminRole));
        assertTrue(target.getRoles().contains(memberRole));
        verify(appUserRepository).save(target);
        // Demotion must never touch workspace-level admin mapping rows.
        verifyNoInteractions(workspaceAdminRepository);
        verify(emailService).sendRoleChangeNotification("Workspace Admin", "ws.admin@company.com", "WORKSPACE_ADMIN", "MEMBER", null, null);
    }

    @Test
    void updateGlobalRole_RejectsSelfChange() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .email("admin@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new java.util.HashSet<>(Set.of(memberRole)))
                .build();
        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.updateGlobalRole("admin@company.com", target.getId(), "WORKSPACE_ADMIN"));
        assertTrue(ex.getMessage().contains("cannot change your own role"));
    }

    @Test
    void updateGlobalRole_RejectsChangingSuperAdminAccount() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Other Super Admin")
                .email("other.admin@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new java.util.HashSet<>(Set.of(globalAdminRole)))
                .build();
        when(appUserRepository.findById(target.getId())).thenReturn(Optional.of(target));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.updateGlobalRole("admin@company.com", target.getId(), "MEMBER"));
        assertTrue(ex.getMessage().contains("super admin"));
    }

    @Test
    void updateGlobalRole_RejectsInvalidRole() {
        AppUser target = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Plain User")
                .email("plain.user@company.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new java.util.HashSet<>(Set.of(memberRole)))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.updateGlobalRole("admin@company.com", target.getId(), "ADMIN"));
        assertTrue(ex.getMessage().contains("MEMBER or WORKSPACE_ADMIN"));
    }
}
