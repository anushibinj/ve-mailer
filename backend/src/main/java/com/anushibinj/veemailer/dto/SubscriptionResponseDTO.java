package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.Frequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponseDTO {
    private UUID id;
    private String recipientEmail;
    private UUID filterId;
    private String filterTitle;
    private Frequency frequency;
}
