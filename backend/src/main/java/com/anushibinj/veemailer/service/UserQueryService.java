package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.RecipientGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides user-listing queries shared across multiple controllers
 * (UserManagementController, WorkspaceController) so no controller
 * needs to depend on repositories directly.
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final AppUserRepository appUserRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;
    private final RecipientGroupRepository recipientGroupRepository;

    /** Returns all registered users enriched with their active subscription counts. */
    public List<UserSummaryDto> getAllUserSummaries() {
        Map<String, Long> countByEmail = emailSubscriberRepository
                .countActiveSubscriptionsGroupedByEmail()
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        return appUserRepository.findAll()
                .stream()
                .map(user -> toDto(user, countByEmail.getOrDefault(user.getEmail(), 0L)))
                .collect(Collectors.toList());
    }

    /** Returns a single user's summary (used after a mutation like a global role change). */
    public UserSummaryDto getUserSummary(java.util.UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        long subscribedFilterCount = emailSubscriberRepository.countActiveSubscriptionsGroupedByEmail()
                .stream()
                .filter(row -> user.getEmail().equals(row[0]))
                .map(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);
        return toDto(user, subscribedFilterCount);
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

    /**
     * Returns a sorted list of emails that appear as recipient-group members but do not
     * have a corresponding application-user account.  These are candidates for onboarding.
     */
    public List<String> getNonAppUserEmails() {
        Set<String> appUserEmails = appUserRepository.findAll()
                .stream()
                .map(u -> u.getEmail().trim().toLowerCase())
                .collect(Collectors.toSet());

        return recipientGroupRepository.findAll()
                .stream()
                .flatMap(group -> group.getMemberEmails().stream())
                .map(email -> email.trim().toLowerCase())
                .filter(email -> !email.isEmpty() && !appUserEmails.contains(email))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
