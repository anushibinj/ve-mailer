package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AdminOnboardUserRequestDto;
import com.anushibinj.veemailer.dto.ApiResponseWrapper;
import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.service.AuthService;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserManagementController {

    private final AppUserRepository appUserRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final AuthService authService;

    /**
     * Returns all registered users enriched with their active subscription counts.
     * The subscription counts are fetched in a single aggregation query to avoid N+1.
     */
    @GetMapping
    public ResponseEntity<List<UserSummaryDto>> getAllUsers() {
        // Fetch counts in one query: Map<email, count>
        Map<String, Long> countByEmail = emailSubscriberRepository
                .countActiveSubscriptionsGroupedByEmail()
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        List<UserSummaryDto> users = appUserRepository.findAll()
                .stream()
                .map(user -> toDto(user, countByEmail.getOrDefault(user.getEmail(), 0L)))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
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

    private UserSummaryDto toDto(AppUser user, long subscribedFilterCount) {
        List<String> roleNames = user.getRoles()
                .stream()
                .map(r -> r.getRoleName())
                .sorted()
                .collect(Collectors.toList());

        return UserSummaryDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roles(roleNames)
                .subscribedFilterCount(subscribedFilterCount)
                .mustSetPassword(user.isMustSetPassword())
                .build();
    }
}
