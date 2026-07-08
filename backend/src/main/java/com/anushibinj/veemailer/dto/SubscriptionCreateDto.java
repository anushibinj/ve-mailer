package com.anushibinj.veemailer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCreateDto {

    @NotNull(message = "Filter ID is required")
    private UUID filterId;

    @NotNull(message = "Schedule is required")
    @Valid
    private ScheduleDto schedule;

    /**
     * Optional: admins and workspace admins may specify the email address of the user
     * to subscribe on their behalf. When omitted (or blank), the requester subscribes themselves.
     */
    @Email(message = "recipientEmail must be a valid email address")
    private String recipientEmail;
}
