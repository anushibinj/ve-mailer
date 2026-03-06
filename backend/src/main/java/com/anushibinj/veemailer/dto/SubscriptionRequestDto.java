package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.Frequency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRequestDto {

    @NotNull
    @Email
    private String email;

    @NotNull
    private ActionType actionType;

    @NotNull
    private UUID workspaceId;

    @NotNull
    private UUID filterId;

    @NotNull
    private Frequency frequency;
}
