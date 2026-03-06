package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionRequestDto;
import com.anushibinj.veemailer.dto.VerificationRequestDto;
import com.anushibinj.veemailer.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/request")
    public ResponseEntity<String> requestSubscription(@Valid @RequestBody SubscriptionRequestDto request) {
        subscriptionService.requestSubscription(
                request.getEmail(),
                request.getActionType(),
                request.getWorkspaceId(),
                request.getFilterId(),
                request.getFrequency()
        );
        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifySubscription(@Valid @RequestBody VerificationRequestDto request) {
        try {
            subscriptionService.verifyAndExecute(request.getEmail(), request.getOtp());
            return ResponseEntity.ok("Subscription action completed successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Verification failed: " + e.getMessage());
        }
    }
}
