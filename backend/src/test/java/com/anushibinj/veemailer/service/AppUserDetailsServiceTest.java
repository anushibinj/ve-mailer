package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Role;
import com.anushibinj.veemailer.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    @Test
    void loadUserByUsername_Success() {
        Role memberRole = Role.builder().id(UUID.randomUUID()).roleName("MEMBER").build();
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test@company.com")
                .passwordHash("hashed-password")
                .enabled(true)
                .roles(Set.of(memberRole))
                .build();

        when(appUserRepository.findByEmail("test@company.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = appUserDetailsService.loadUserByUsername("test@company.com");

        assertNotNull(userDetails);
        assertEquals("test@company.com", userDetails.getUsername());
        assertEquals("hashed-password", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER")));
    }

    @Test
    void loadUserByUsername_NotFound() {
        when(appUserRepository.findByEmail("nonexistent@company.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> appUserDetailsService.loadUserByUsername("nonexistent@company.com"));
    }

    @Test
    void loadUserByUsername_DisabledUser() {
        Role memberRole = Role.builder().id(UUID.randomUUID()).roleName("MEMBER").build();
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Disabled User")
                .email("disabled@company.com")
                .passwordHash("hashed-password")
                .enabled(false)
                .roles(Set.of(memberRole))
                .build();

        when(appUserRepository.findByEmail("disabled@company.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = appUserDetailsService.loadUserByUsername("disabled@company.com");

        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled());
    }
}
