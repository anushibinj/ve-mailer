package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final OtpRequestRepository otpRequestRepository;

    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Running cleanup job for expired OTP requests");
        otpRequestRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
