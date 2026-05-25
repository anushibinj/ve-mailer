package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Role;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private AppUserRepository appUserRepository;

    @MockBean
    private EmailSubscriberRepository emailSubscriberRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void testGetAllUsers_returnsUserSummaries() throws Exception {
        Role adminRole = Role.builder().id(UUID.randomUUID()).roleName("ROLE_ADMIN").build();
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Alice Admin")
                .email("alice@company.com")
                .passwordHash("$2a$hash")
                .roles(Set.of(adminRole))
                .build();

        when(appUserRepository.findAll()).thenReturn(List.of(user));
        List<Object[]> counts = new ArrayList<>();
        counts.add(new Object[]{"alice@company.com", 3L});
        when(emailSubscriberRepository.countActiveSubscriptionsGroupedByEmail())
                .thenReturn(counts);

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
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Bob Member")
                .email("bob@company.com")
                .passwordHash("$2a$hash")
                .roles(Set.of())
                .build();

        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(emailSubscriberRepository.countActiveSubscriptionsGroupedByEmail())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("bob@company.com"))
                .andExpect(jsonPath("$[0].subscribedFilterCount").value(0));
    }
}
