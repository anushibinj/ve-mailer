package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.InviteMagicLink;
import com.anushibinj.veemailer.repository.InviteMagicLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteMagicLinkServiceTest {

    @Mock
    private InviteMagicLinkRepository inviteMagicLinkRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private InviteMagicLinkService inviteMagicLinkService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inviteMagicLinkService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(inviteMagicLinkService, "inviteLinkExpirationMinutes", 10);
        ReflectionTestUtils.setField(inviteMagicLinkService, "inviteLinkBaseResendSeconds", 30);
    }

    @Test
    void createAndSendInviteMagicLink_NewRequest_SavesAndSends() {
        when(inviteMagicLinkRepository.findByEmail("invitee@company.com")).thenReturn(Optional.empty());
        when(inviteMagicLinkRepository.save(any(InviteMagicLink.class))).thenAnswer(inv -> inv.getArgument(0));

        inviteMagicLinkService.createAndSendInviteMagicLink("invitee@company.com", "Invitee");

        ArgumentCaptor<InviteMagicLink> entityCaptor = ArgumentCaptor.forClass(InviteMagicLink.class);
        verify(inviteMagicLinkRepository).save(entityCaptor.capture());
        InviteMagicLink saved = entityCaptor.getValue();
        assertEquals("invitee@company.com", saved.getEmail());
        assertNotNull(saved.getTokenHash());
        assertNotNull(saved.getExpiresAt());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInviteMagicLinkEmail(
                eq("invitee@company.com"),
                eq("Invitee"),
                linkCaptor.capture(),
                eq(10));
        assertNotNull(linkCaptor.getValue());
    }

    @Test
    void createAndSendInviteMagicLink_CooldownEnforced() {
        InviteMagicLink existing = InviteMagicLink.builder()
                .email("invitee@company.com")
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .resendCount(0)
                .lastSentAt(LocalDateTime.now().minusSeconds(10))
                .build();
        when(inviteMagicLinkRepository.findByEmail("invitee@company.com")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> inviteMagicLinkService.createAndSendInviteMagicLink("invitee@company.com", "Invitee"));
        verify(inviteMagicLinkRepository, never()).save(any());
    }

    @Test
    void validateInviteMagicLink_InvalidWhenUnknownToken() {
        when(inviteMagicLinkRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        InviteMagicLinkService.ValidationResult result = inviteMagicLinkService.validateInviteMagicLink("abc");
        assertEquals(InviteMagicLinkService.ValidationStatus.INVALID, result.status());
    }
}
