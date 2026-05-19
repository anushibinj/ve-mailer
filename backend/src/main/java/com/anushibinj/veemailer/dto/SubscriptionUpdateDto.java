package com.anushibinj.veemailer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpdateDto {

    @NotNull(message = "Schedule is required")
    @Valid
    private ScheduleDto schedule;
}
