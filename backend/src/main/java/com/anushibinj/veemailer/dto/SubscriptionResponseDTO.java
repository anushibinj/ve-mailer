package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.Status;
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
    private ScheduleDto schedule;
    /** Non-null when this is a group subscription (recipientEmail will be null). */
    private UUID groupId;
    private String groupName;
    private Integer groupMemberCount;
    /** ACTIVE means the subscription is sending emails; DISABLED means it is paused. */
    private Status status;
}
