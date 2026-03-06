package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.OtpRequest;
import com.anushibinj.veemailer.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public void createAndSendOtp(String email, ActionType actionType, String payload) {
        String otp = generateOtp();
        String hash = passwordEncoder.encode(otp);

        Optional<OtpRequest> existingOtpOpt = otpRequestRepository.findByEmail(email);
        OtpRequest otpRequest = existingOtpOpt.orElse(new OtpRequest());

        otpRequest.setEmail(email);
        otpRequest.setActionType(actionType);
        otpRequest.setPayload(payload);
        otpRequest.setOtpHash(hash);
        otpRequest.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        otpRequestRepository.save(otpRequest);

        // Asynchronously send email
        emailService.sendOtpEmail(email, otp);
    }

    public OtpRequest validateOtp(String email, String plainOtp) {
        Optional<OtpRequest> optRequest = otpRequestRepository.findByEmail(email);

        if (optRequest.isEmpty()) {
            throw new IllegalArgumentException("No OTP request found for this email.");
        }

        OtpRequest request = optRequest.get();
        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired.");
        }

        if (!passwordEncoder.matches(plainOtp, request.getOtpHash())) {
            throw new IllegalArgumentException("Invalid OTP.");
        }

        return request;
    }

    public void cleanupOtp(OtpRequest request) {
        otpRequestRepository.delete(request);
    }
}
