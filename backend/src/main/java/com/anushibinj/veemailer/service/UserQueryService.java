package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
