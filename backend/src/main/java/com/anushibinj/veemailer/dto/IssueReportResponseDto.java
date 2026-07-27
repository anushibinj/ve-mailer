package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.IssueStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueReportResponseDto {
    private UUID id;
    private String reporterEmail;
    private String message;
    private IssueStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean hasScreenshot;
    private String screenshotContentType;
    private String screenshotBase64;
}
