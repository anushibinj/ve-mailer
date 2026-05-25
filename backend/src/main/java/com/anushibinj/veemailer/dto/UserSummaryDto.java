package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDto {

    private UUID id;
    private String name;
    private String email;
    /** Role names, e.g. ["ROLE_ADMIN", "ROLE_MEMBER"] */
    private List<String> roles;
    /** Number of active subscriptions the user currently holds. */
    private long subscribedFilterCount;
}
