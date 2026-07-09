package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.AuthService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserQueryService userQueryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private AuthService authService;

    @Test
    void testGetAllUsers_returnsUserSummaries() throws Exception {
        UserSummaryDto user = UserSummaryDto.builder()
                .id(UUID.randomUUID())
                .name("Alice Admin")
                .email("alice@company.com")
                .roles(List.of("ROLE_ADMIN"))
                .subscribedFilterCount(3)
                .mustSetPassword(false)
                .build();

        when(userQueryService.getAllUserSummaries()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Alice Admin"))
                .andExpect(jsonPath("$[0].email").value("alice@company.com"))
                .andExpect(jsonPath("$[0].roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$[0].subscribedFilterCount").value(3));
    }

    @Test
    void testGetAllUsers_noSubscriptions_returnsZeroCount() throws Exception {
        UserSummaryDto user = UserSummaryDto.builder()
                .id(UUID.randomUUID())
                .name("Bob Member")
                .email("bob@company.com")
                .roles(List.of())
                .subscribedFilterCount(0)
                .mustSetPassword(false)
                .build();

        when(userQueryService.getAllUserSummaries()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("bob@company.com"))
                .andExpect(jsonPath("$[0].subscribedFilterCount").value(0));
    }
}
