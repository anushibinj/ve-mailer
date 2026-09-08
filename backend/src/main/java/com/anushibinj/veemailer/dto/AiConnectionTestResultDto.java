package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConnectionTestResultDto {
    private boolean success;
    private String message;
    /** The AI's reply to the test "Hi" message, present only when success is true. */
    private String reply;
}
