package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralSettingsDto {

    /**
     * Maximum number of tickets per mail report.
     * Positive integer to apply a limit; {@code -1} for unlimited.
     */
    private int queryLimit;
}
