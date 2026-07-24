package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteMagicLinkVerificationResponseDto {
    private String status;
    private String message;
    private String email;
}
