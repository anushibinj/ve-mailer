package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.NotificationPreferencesResponseDto;
import com.anushibinj.veemailer.dto.NotificationPreferencesUpdateDto;
import com.anushibinj.veemailer.model.NotificationPreferences;
import com.anushibinj.veemailer.repository.NotificationPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferencesServiceTest {

    @Mock
    private NotificationPreferencesRepository repository;

    @InjectMocks
    private NotificationPreferencesService service;

    // --- get() ---

    @Test
    void get_WhenNoPreferencesExist_ReturnsNotConfigured() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        NotificationPreferencesResponseDto result = service.get();

        assertFalse(result.isConfigured());
    }

    @Test
    void get_WhenPreferencesExist_ReturnsMaskedPasswordAndFields() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("smtp.example.com")
                .port(587)
                .fromAddress("noreply@example.com")
                .requiresAuth(true)
                .username("user@example.com")
                .password("secret123")
                .startTlsEnabled(true)
                .build();
        when(repository.findAll()).thenReturn(List.of(prefs));

        NotificationPreferencesResponseDto result = service.get();

        assertTrue(result.isConfigured());
        assertEquals("smtp.example.com", result.getHost());
        assertEquals(587, result.getPort());
        assertEquals("noreply@example.com", result.getFromAddress());
        assertTrue(result.isRequiresAuth());
        assertEquals("user@example.com", result.getUsername());
        assertEquals("(unchanged)", result.getPassword());
        assertTrue(result.isStartTlsEnabled());
    }

    @Test
    void get_WhenNoAuth_ReturnsNullPassword() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("relay.internal")
                .port(25)
                .fromAddress("noreply@example.com")
                .requiresAuth(false)
                .startTlsEnabled(false)
                .build();
        when(repository.findAll()).thenReturn(List.of(prefs));

        NotificationPreferencesResponseDto result = service.get();

        assertTrue(result.isConfigured());
        assertFalse(result.isRequiresAuth());
        assertNull(result.getPassword());
    }

    // --- update() with auth ---

    @Test
    void update_FirstTime_WithAuth_CreatesNewPreferences() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        when(repository.save(any())).thenAnswer(inv -> {
            NotificationPreferences saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("smtp.test.com");
        dto.setPort(587);
        dto.setFromAddress("from@test.com");
        dto.setRequiresAuth(true);
        dto.setUsername("admin@test.com");
        dto.setPassword("newpassword");
        dto.setStartTlsEnabled(false);

        NotificationPreferencesResponseDto result = service.update(dto);

        assertTrue(result.isConfigured());
        assertEquals("smtp.test.com", result.getHost());
        assertEquals("from@test.com", result.getFromAddress());
        assertEquals("(unchanged)", result.getPassword());
        verify(repository).save(any());
    }

    @Test
    void update_FirstTime_WithAuth_ThrowsWhenPasswordIsPlaceholder() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("smtp.test.com");
        dto.setPort(587);
        dto.setFromAddress("from@test.com");
        dto.setRequiresAuth(true);
        dto.setUsername("admin@test.com");
        dto.setPassword("(unchanged)");
        dto.setStartTlsEnabled(false);

        assertThrows(IllegalArgumentException.class, () -> service.update(dto));
    }

    @Test
    void update_FirstTime_WithAuth_ThrowsWhenUsernameBlank() {
        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("smtp.test.com");
        dto.setPort(587);
        dto.setFromAddress("from@test.com");
        dto.setRequiresAuth(true);
        dto.setUsername("");
        dto.setPassword("pass");
        dto.setStartTlsEnabled(false);

        assertThrows(IllegalArgumentException.class, () -> service.update(dto));
    }

    @Test
    void update_FirstTime_NoAuth_CreatesWithoutCredentials() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        when(repository.save(any())).thenAnswer(inv -> {
            NotificationPreferences saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("relay.internal");
        dto.setPort(25);
        dto.setFromAddress("noreply@example.com");
        dto.setRequiresAuth(false);
        dto.setStartTlsEnabled(false);

        NotificationPreferencesResponseDto result = service.update(dto);

        assertTrue(result.isConfigured());
        assertFalse(result.isRequiresAuth());
        assertNull(result.getPassword());
        verify(repository).save(any());
    }

    @Test
    void update_Existing_WithAuth_PreservesPasswordWhenPlaceholder() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("old.host.com")
                .port(25)
                .fromAddress("old@example.com")
                .requiresAuth(true)
                .username("old@example.com")
                .password("existingSecret")
                .startTlsEnabled(false)
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("new.host.com");
        dto.setPort(587);
        dto.setFromAddress("new@example.com");
        dto.setRequiresAuth(true);
        dto.setUsername("new@example.com");
        dto.setPassword("(unchanged)");
        dto.setStartTlsEnabled(true);

        service.update(dto);

        assertEquals("existingSecret", existing.getPassword());
        assertEquals("new.host.com", existing.getHost());
        assertEquals(587, existing.getPort());
        assertEquals("new@example.com", existing.getUsername());
        assertTrue(existing.isStartTlsEnabled());
    }

    @Test
    void update_Existing_WithAuth_UpdatesPasswordWhenNewValueProvided() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("old.host.com")
                .port(25)
                .fromAddress("old@example.com")
                .requiresAuth(true)
                .username("old@example.com")
                .password("existingSecret")
                .startTlsEnabled(false)
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("old.host.com");
        dto.setPort(25);
        dto.setFromAddress("old@example.com");
        dto.setRequiresAuth(true);
        dto.setUsername("old@example.com");
        dto.setPassword("brandNewSecret");
        dto.setStartTlsEnabled(false);

        service.update(dto);

        assertEquals("brandNewSecret", existing.getPassword());
    }

    @Test
    void update_Existing_SwitchingToNoAuth_ClearsCredentials() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("smtp.host.com")
                .port(587)
                .fromAddress("noreply@example.com")
                .requiresAuth(true)
                .username("user@example.com")
                .password("secret")
                .startTlsEnabled(true)
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesUpdateDto dto = new NotificationPreferencesUpdateDto();
        dto.setHost("relay.internal");
        dto.setPort(25);
        dto.setFromAddress("noreply@example.com");
        dto.setRequiresAuth(false);
        dto.setStartTlsEnabled(false);

        service.update(dto);

        assertFalse(existing.isRequiresAuth());
        assertNull(existing.getUsername());
        assertNull(existing.getPassword());
    }

    // --- getEntity() ---

    @Test
    void getEntity_ReturnsNullWhenEmpty() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        assertNull(service.getEntity());
    }

    @Test
    void getEntity_ReturnsEntityWhenExists() {
        NotificationPreferences prefs = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .host("smtp.example.com")
                .port(587)
                .fromAddress("noreply@example.com")
                .requiresAuth(true)
                .username("user@example.com")
                .password("secret")
                .startTlsEnabled(true)
                .build();
        when(repository.findAll()).thenReturn(List.of(prefs));

        NotificationPreferences result = service.getEntity();
        assertNotNull(result);
        assertEquals("smtp.example.com", result.getHost());
    }
}
