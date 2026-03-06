package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.Frequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponseDTO {
    private String recipientEmail;
    private String filterTitle;
    private Frequency frequency;
}
