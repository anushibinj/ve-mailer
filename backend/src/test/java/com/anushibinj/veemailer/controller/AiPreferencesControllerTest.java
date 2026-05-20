package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.AiPreferencesUpdateDto;
import com.anushibinj.veemailer.service.AiPreferencesService;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiPreferencesController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiPreferencesService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void get_ReturnsNotConfiguredWhenEmpty() throws Exception {
        when(service.get()).thenReturn(AiPreferencesResponseDto.builder()
                .configured(false)
                .build());

        mockMvc.perform(get("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void get_ReturnsConfiguredPreferencesWithMaskedApiKey() throws Exception {
        when(service.get()).thenReturn(AiPreferencesResponseDto.builder()
                .apiKey("(unchanged)")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/chat/completions")
                .model("gpt-4.1-mini")
                .configured(true)
                .build());

        mockMvc.perform(get("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.apiKey").value("(unchanged)"))
                .andExpect(jsonPath("$.baseUrl").value("https://api.openai.com"))
                .andExpect(jsonPath("$.chatCompletionsPath").value("/chat/completions"))
                .andExpect(jsonPath("$.model").value("gpt-4.1-mini"));
    }

    @Test
    void update_ReturnsUpdatedPreferences() throws Exception {
        when(service.update(any())).thenReturn(AiPreferencesResponseDto.builder()
                .apiKey("(unchanged)")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/v1/chat/completions")
                .model("gpt-4o")
                .configured(true)
                .build());

        AiPreferencesUpdateDto request = new AiPreferencesUpdateDto();
        request.setApiKey("sk-new-key");
        request.setBaseUrl("https://api.openai.com");
        request.setChatCompletionsPath("/v1/chat/completions");
        request.setModel("gpt-4o");

        mockMvc.perform(put("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.apiKey").value("(unchanged)"))
                .andExpect(jsonPath("$.model").value("gpt-4o"));
    }

    @Test
    void update_ValidationFailsWhenBaseUrlBlank() throws Exception {
        AiPreferencesUpdateDto request = new AiPreferencesUpdateDto();
        request.setApiKey("sk-key");
        request.setBaseUrl("");
        request.setChatCompletionsPath("/chat/completions");
        request.setModel("gpt-4.1-mini");

        mockMvc.perform(put("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ValidationFailsWhenBaseUrlMissingScheme() throws Exception {
        AiPreferencesUpdateDto request = new AiPreferencesUpdateDto();
        request.setApiKey("sk-key");
        request.setBaseUrl("api.openai.com");
        request.setChatCompletionsPath("/chat/completions");
        request.setModel("gpt-4.1-mini");

        mockMvc.perform(put("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_ValidationFailsWhenModelBlank() throws Exception {
        AiPreferencesUpdateDto request = new AiPreferencesUpdateDto();
        request.setApiKey("sk-key");
        request.setBaseUrl("https://api.openai.com");
        request.setChatCompletionsPath("/chat/completions");
        request.setModel("");

        mockMvc.perform(put("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
