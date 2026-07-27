package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.IssueReportResponseDto;
import com.anushibinj.veemailer.model.IssueReport;
import com.anushibinj.veemailer.model.IssueStatus;
import com.anushibinj.veemailer.repository.IssueReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueReportServiceTest {

    @Mock
    private IssueReportRepository issueReportRepository;

    @InjectMocks
    private IssueReportService issueReportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(issueReportService, "maxScreenshotBytes", 1024L);
    }

    @Test
    void submitIssue_requiresMessageOrScreenshot() {
        assertThatThrownBy(() -> issueReportService.submitIssue("   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either message or screenshot is required");
    }

    @Test
    void submitIssue_usesAuthenticatedEmailWhenReporterEmailMissing() {
        UUID id = UUID.randomUUID();
        when(issueReportRepository.save(any(IssueReport.class))).thenAnswer(invocation -> {
            IssueReport issue = invocation.getArgument(0);
            issue.setId(id);
            return issue;
        });

        IssueReportResponseDto result = issueReportService.submitIssue("Test issue", null, null, "member@company.com");

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getReporterEmail()).isEqualTo("member@company.com");
        assertThat(result.getStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(result.isHasScreenshot()).isFalse();
    }

    @Test
    void submitIssue_rejectsScreenshotLargerThanConfiguredMax() {
        MultipartFile screenshot = new org.springframework.mock.web.MockMultipartFile(
                "screenshot",
                "img.png",
                "image/png",
                new byte[2048]
        );

        assertThatThrownBy(() -> issueReportService.submitIssue(null, null, screenshot, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    void updateStatus_updatesAndReturnsIssue() {
        UUID id = UUID.randomUUID();
        IssueReport existing = IssueReport.builder()
                .id(id)
                .status(IssueStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(issueReportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(issueReportRepository.save(any(IssueReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IssueReportResponseDto updated = issueReportService.updateStatus(id, IssueStatus.RESOLVED);

        assertThat(updated.getStatus()).isEqualTo(IssueStatus.RESOLVED);
    }
}
