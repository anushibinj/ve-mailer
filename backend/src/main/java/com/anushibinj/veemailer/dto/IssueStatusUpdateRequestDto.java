package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.IssueStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueStatusUpdateRequestDto {

    @NotNull(message = "Issue status is required")
    private IssueStatus status;
}
