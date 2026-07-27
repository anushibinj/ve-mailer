package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.IssueReportResponseDto;
import com.anushibinj.veemailer.model.IssueReport;
import com.anushibinj.veemailer.model.IssueStatus;
import com.anushibinj.veemailer.repository.IssueReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueReportService {

    private final IssueReportRepository issueReportRepository;

    @Value("${veemailer.issues.max-screenshot-bytes:1048576}")
    private long maxScreenshotBytes;

    public IssueReportResponseDto submitIssue(
            String message,
            String reporterEmail,
            MultipartFile screenshot,
            String authenticatedEmail) {
        String normalizedMessage = normalizeToNull(message);
        String normalizedEmail = normalizeToNull(reporterEmail);
        String effectiveEmail = normalizedEmail != null ? normalizedEmail : normalizeToNull(authenticatedEmail);

        boolean hasScreenshot = screenshot != null && !screenshot.isEmpty();
        if (normalizedMessage == null && !hasScreenshot) {
            throw new IllegalArgumentException("Either message or screenshot is required.");
        }

        byte[] screenshotBytes = null;
        String screenshotContentType = null;
        String screenshotFileName = null;
        if (hasScreenshot) {
            if (screenshot.getSize() > maxScreenshotBytes) {
                throw new IllegalArgumentException(
                        "Screenshot exceeds the maximum allowed size of " + maxScreenshotBytes + " bytes."
                );
            }
            screenshotBytes = readMultipartBytes(screenshot);
            screenshotContentType = normalizeToNull(screenshot.getContentType());
            screenshotFileName = normalizeToNull(screenshot.getOriginalFilename());
        }

        IssueReport toSave = IssueReport.builder()
                .reporterEmail(effectiveEmail)
                .message(normalizedMessage)
                .screenshotData(screenshotBytes)
                .screenshotContentType(screenshotContentType)
                .screenshotFileName(screenshotFileName)
                .status(IssueStatus.OPEN)
                .build();
        IssueReport saved = issueReportRepository.save(toSave);
        return toResponseDto(saved, false);
    }

    public List<IssueReportResponseDto> listIssues() {
        return issueReportRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(issue -> toResponseDto(issue, true))
                .toList();
    }

    public IssueReportResponseDto updateStatus(UUID issueId, IssueStatus status) {
        IssueReport issue = issueReportRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        issue.setStatus(status);
        IssueReport saved = issueReportRepository.save(issue);
        return toResponseDto(saved, true);
    }

    private IssueReportResponseDto toResponseDto(IssueReport issue, boolean includeScreenshotData) {
        byte[] screenshotData = issue.getScreenshotData();
        boolean hasScreenshot = screenshotData != null && screenshotData.length > 0;
        String screenshotBase64 = null;
        if (includeScreenshotData && hasScreenshot) {
            screenshotBase64 = Base64.getEncoder().encodeToString(screenshotData);
        }

        return IssueReportResponseDto.builder()
                .id(issue.getId())
                .reporterEmail(issue.getReporterEmail())
                .message(issue.getMessage())
                .status(issue.getStatus())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .hasScreenshot(hasScreenshot)
                .screenshotContentType(issue.getScreenshotContentType())
                .screenshotBase64(screenshotBase64)
                .build();
    }

    private byte[] readMultipartBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read uploaded screenshot.", ex);
        }
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
