package com.anushibinj.veemailer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.anushibinj.veemailer.model.TriageSlaThreshold;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpdateDto {

    @NotNull(message = "Schedule is required")
    @Valid
    private ScheduleDto schedule;

    /**
     * Optional threshold for Triage SLA subscriptions.
     * Defaults to GREEN when omitted.
     */
    private TriageSlaThreshold triageSlaThreshold;
}
