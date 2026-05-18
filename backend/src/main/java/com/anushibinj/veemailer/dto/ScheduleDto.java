package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.ScheduleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleDto {

    @NotNull(message = "Schedule type must not be null")
    private ScheduleType type;

    @NotNull(message = "Hours list must not be null")
    @NotEmpty(message = "At least one notification hour must be specified")
    private List<@NotNull @Min(value = 0, message = "Hour must be between 0 and 23") @Max(value = 23, message = "Hour must be between 0 and 23") Integer> hours;
}
