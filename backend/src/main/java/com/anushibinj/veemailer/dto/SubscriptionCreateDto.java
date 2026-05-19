package com.anushibinj.veemailer.dto;

import jakarta.validation.Valid;
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
}
