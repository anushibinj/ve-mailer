package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload used by a Super Admin to promote/demote a user's global role
 * between MEMBER (plain user) and WORKSPACE_ADMIN.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGlobalRoleUpdateRequestDto {

    /** Target global role — must be "MEMBER" or "WORKSPACE_ADMIN". */
    @NotBlank(message = "Role is required")
    private String role;
}
