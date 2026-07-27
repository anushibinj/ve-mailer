package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.ApiResponseWrapper;
import com.anushibinj.veemailer.dto.IssueReportResponseDto;
import com.anushibinj.veemailer.dto.IssueStatusUpdateRequestDto;
import com.anushibinj.veemailer.dto.IssueUploadConfigDto;
import com.anushibinj.veemailer.service.IssueReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IssueReportController {

    private final IssueReportService issueReportService;

    @GetMapping("/api/v1/issues/config")
    public ResponseEntity<IssueUploadConfigDto> getIssueUploadConfig() {
        return ResponseEntity.ok(issueReportService.getUploadConfig());
    }

    @PostMapping(path = "/api/v1/issues", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseWrapper> submitIssue(
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "reporterEmail", required = false) String reporterEmail,
            @RequestPart(name = "screenshot", required = false) MultipartFile screenshot,
            Authentication authentication) {
        String authenticatedEmail = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName()
                : null;
        if ("anonymousUser".equalsIgnoreCase(authenticatedEmail)) {
            authenticatedEmail = null;
        }
        issueReportService.submitIssue(message, reporterEmail, screenshot, authenticatedEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseWrapper.success("Issue submitted successfully."));
    }

    @GetMapping("/api/admin/issues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<IssueReportResponseDto>> getIssues() {
        return ResponseEntity.ok(issueReportService.listIssues());
    }

    @PatchMapping("/api/admin/issues/{issueId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssueReportResponseDto> updateIssueStatus(
            @PathVariable UUID issueId,
            @RequestBody @Valid IssueStatusUpdateRequestDto request) {
        return ResponseEntity.ok(issueReportService.updateStatus(issueId, request.getStatus()));
    }
}
