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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
     * Creates the account and sends an invite OTP to the given email.
     */
    @PostMapping
    public ResponseEntity<ApiResponseWrapper> onboardUser(@Valid @RequestBody AdminOnboardUserRequestDto request) {
        String message = authService.onboardUser(request);
        return ResponseEntity.ok(ApiResponseWrapper.success(message));
    }
}
