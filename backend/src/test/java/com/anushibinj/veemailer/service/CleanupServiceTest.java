package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.repository.OtpRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

    @Mock
    private OtpRequestRepository otpRequestRepository;

    @InjectMocks
    private CleanupService cleanupService;

    @Test
    void testCleanupExpiredOtps_CallsRepositoryWithCurrentTime() {
        LocalDateTime before = LocalDateTime.now();

        cleanupService.cleanupExpiredOtps();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(otpRequestRepository, times(1)).deleteByExpiresAtBefore(captor.capture());

        LocalDateTime captured = captor.getValue();
        LocalDateTime after = LocalDateTime.now();

        // The captured timestamp should be between our before/after bounds
        assertFalse(captured.isBefore(before), "Timestamp should not be before the test started");
        assertFalse(captured.isAfter(after), "Timestamp should not be after the test completed");
    }

    @Test
    void testCleanupExpiredOtps_InvokedOnlyOnce() {
        cleanupService.cleanupExpiredOtps();

        verify(otpRequestRepository, times(1)).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
