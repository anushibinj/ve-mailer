package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.IssueReportResponseDto;
import com.anushibinj.veemailer.model.IssueStatus;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.IssueReportService;
import com.anushibinj.veemailer.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IssueReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class IssueReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IssueReportService issueReportService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void submitIssue_allowsPublicSubmission() throws Exception {
        when(issueReportService.submitIssue(any(), any(), any(), any()))
                .thenReturn(IssueReportResponseDto.builder().id(UUID.randomUUID()).build());

        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "bug.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(screenshot)
                        .param("message", "Something broke"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Issue submitted successfully."));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void admin_canListIssues() throws Exception {
        IssueReportResponseDto dto = IssueReportResponseDto.builder()
                .id(UUID.randomUUID())
                .status(IssueStatus.OPEN)
                .message("Issue text")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(issueReportService.listIssues()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/issues").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].message").value("Issue text"));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void admin_canUpdateIssueStatus() throws Exception {
        UUID issueId = UUID.randomUUID();
        IssueReportResponseDto dto = IssueReportResponseDto.builder()
                .id(issueId)
                .status(IssueStatus.RESOLVED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(issueReportService.updateStatus(eq(issueId), eq(IssueStatus.RESOLVED))).thenReturn(dto);

        mockMvc.perform(patch("/api/admin/issues/{issueId}/status", issueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
