package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AdminOnboardUserRequestDto;
import com.anushibinj.veemailer.dto.ApiResponseWrapper;
import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.service.AuthService;
import com.anushibinj.veemailer.service.UserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserQueryService userQueryService;
    private final AuthService authService;

    /**
     * Returns all registered users enriched with their active subscription counts.
     */
    @GetMapping
    public ResponseEntity<List<UserSummaryDto>> getAllUsers() {
        return ResponseEntity.ok(userQueryService.getAllUserSummaries());
    }

    /**
     * Onboards a new user without requiring self-signup.
     * Creates the account and sends an invite magic link to the given email.
     */
    @PostMapping
    public ResponseEntity<ApiResponseWrapper> onboardUser(@Valid @RequestBody AdminOnboardUserRequestDto request) {
        String message = authService.onboardUser(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    /**
     * Resends an invite magic link for users who have not completed onboarding.
     */
    @PostMapping("/{userId}/resend-invite")
    public ResponseEntity<ApiResponseWrapper> resendInvite(@PathVariable java.util.UUID userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        String message = authService.resendPendingInviteByAdmin(authentication.getName(), userId);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    /**
     * Permanently deletes a user account and related records.
     * Super admins cannot delete their own account.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponseWrapper> deleteUser(@PathVariable java.util.UUID userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        String message = authService.deleteUserBySuperAdmin(authentication.getName(), userId);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }

    /**
     * Returns emails of people who appear in recipient-group member lists but
     * have no application-user account.  These are candidates for onboarding.
     */
    @GetMapping("/non-app-users")
    public ResponseEntity<List<String>> getNonAppUsers() {
        return ResponseEntity.ok(userQueryService.getNonAppUserEmails());
    }
}
