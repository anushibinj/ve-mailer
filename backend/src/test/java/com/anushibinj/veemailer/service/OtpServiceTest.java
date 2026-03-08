package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.OtpRequest;
import com.anushibinj.veemailer.repository.OtpRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpRequestRepository otpRequestRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OtpService otpService;

    @Test
    void testGenerateOtp_ShouldReturn6Digits() {
        String otp = otpService.generateOtp();
        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void testCreateAndSendOtp_NewRequest() {
        when(otpRequestRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");

        otpService.createAndSendOtp("test@test.com", ActionType.SUBSCRIBE, "payload");

        ArgumentCaptor<OtpRequest> captor = ArgumentCaptor.forClass(OtpRequest.class);
        verify(otpRequestRepository, times(1)).save(captor.capture());

        OtpRequest saved = captor.getValue();
        assertEquals("test@test.com", saved.getEmail());
        assertEquals(ActionType.SUBSCRIBE, saved.getActionType());
        assertEquals("payload", saved.getPayload());
        assertEquals("hashed-otp", saved.getOtpHash());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));

        verify(emailService, times(1)).sendOtpEmail(eq("test@test.com"), anyString());
    }

    @Test
    void testValidateOtp_Success() {
        OtpRequest request = new OtpRequest();
        request.setOtpHash("hashed-otp");
        request.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(otpRequestRepository.findByEmail("test@test.com")).thenReturn(Optional.of(request));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);

        OtpRequest result = otpService.validateOtp("test@test.com", "123456");

        assertNotNull(result);
    }

    @Test
    void testValidateOtp_NotFound() {
        when(otpRequestRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> otpService.validateOtp("test@test.com", "123456"));
    }

    @Test
    void testValidateOtp_Expired() {
        OtpRequest request = new OtpRequest();
        request.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(otpRequestRepository.findByEmail("test@test.com")).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class, () -> otpService.validateOtp("test@test.com", "123456"));
    }

    @Test
    void testValidateOtp_Invalid() {
        OtpRequest request = new OtpRequest();
        request.setOtpHash("hashed-otp");
        request.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(otpRequestRepository.findByEmail("test@test.com")).thenReturn(Optional.of(request));
        when(passwordEncoder.matches("wrong", "hashed-otp")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> otpService.validateOtp("test@test.com", "wrong"));
    }

    @Test
    void testCreateAndSendOtp_ExistingRequest_UpdatesRecord() {
        // Existing OTP record already exists — should reuse and update it, not create a new one
        OtpRequest existing = new OtpRequest();
        existing.setEmail("test@test.com");
        existing.setOtpHash("old-hash");

        when(otpRequestRepository.findByEmail("test@test.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        otpService.createAndSendOtp("test@test.com", ActionType.UPDATE, "new-payload");

        ArgumentCaptor<OtpRequest> captor = ArgumentCaptor.forClass(OtpRequest.class);
        verify(otpRequestRepository, times(1)).save(captor.capture());

        OtpRequest saved = captor.getValue();
        // Same object reference should be updated
        assertEquals("test@test.com", saved.getEmail());
        assertEquals(ActionType.UPDATE, saved.getActionType());
        assertEquals("new-payload", saved.getPayload());
        assertEquals("new-hash", saved.getOtpHash());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));

        verify(emailService, times(1)).sendOtpEmail(eq("test@test.com"), anyString());
    }

    @Test
    void testCleanupOtp_DeletesRecord() {
        OtpRequest request = new OtpRequest();
        request.setEmail("cleanup@test.com");

        otpService.cleanupOtp(request);

        verify(otpRequestRepository, times(1)).delete(request);
    }
}
