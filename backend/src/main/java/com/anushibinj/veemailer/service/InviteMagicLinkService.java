package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.InviteMagicLink;
import com.anushibinj.veemailer.repository.InviteMagicLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InviteMagicLinkService {

    private final InviteMagicLinkRepository inviteMagicLinkRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.auth.invite-link.expiration-minutes:10}")
    private int inviteLinkExpirationMinutes;

    @Value("${app.auth.invite-link.base-resend-seconds:30}")
    private int inviteLinkBaseResendSeconds;

    public enum ValidationStatus {
        VALID,
        INVALID,
        EXPIRED,
        USED
    }

    public record ValidationResult(ValidationStatus status, String email) {}

    @Transactional
    public void createAndSendInviteMagicLink(String rawEmail, String name) {
        String email = normalizeEmail(rawEmail);
        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<InviteMagicLink> existingOpt = inviteMagicLinkRepository.findByEmail(email);
        InviteMagicLink inviteMagicLink = existingOpt.orElseGet(InviteMagicLink::new);

        if (inviteMagicLink.getLastSentAt() != null) {
            int requiredSeconds = (inviteMagicLink.getResendCount() + 1) * inviteLinkBaseResendSeconds;
            LocalDateTime allowedAt = inviteMagicLink.getLastSentAt().plusSeconds(requiredSeconds);
            if (allowedAt.isAfter(now)) {
                long remaining = ChronoUnit.SECONDS.between(now, allowedAt);
                throw new IllegalStateException(
                        "Please wait " + remaining + " more second" + (remaining == 1 ? "" : "s")
                                + " before requesting a new invite link.");
            }
        }

        String token = generateToken();
        String tokenHash = hashToken(token);
        inviteMagicLink.setEmail(email);
        inviteMagicLink.setTokenHash(tokenHash);
        inviteMagicLink.setExpiresAt(now.plusMinutes(inviteLinkExpirationMinutes));
        inviteMagicLink.setUsedAt(null);
        inviteMagicLink.setLastSentAt(now);
        inviteMagicLink.setResendCount(existingOpt.isPresent() ? inviteMagicLink.getResendCount() + 1 : 0);

        inviteMagicLinkRepository.save(inviteMagicLink);

        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String magicLink = frontendUrl + "/accept-invite?token=" + encodedToken;
        emailService.sendInviteMagicLinkEmail(email, name, magicLink, inviteLinkExpirationMinutes);
    }

    @Transactional(readOnly = true)
    public ValidationResult validateInviteMagicLink(String token) {
        String tokenHash = hashToken(token);
        Optional<InviteMagicLink> inviteOpt = inviteMagicLinkRepository.findByTokenHash(tokenHash);
        if (inviteOpt.isEmpty()) {
            return new ValidationResult(ValidationStatus.INVALID, null);
        }

        InviteMagicLink inviteMagicLink = inviteOpt.get();
        if (inviteMagicLink.getUsedAt() != null) {
            return new ValidationResult(ValidationStatus.USED, inviteMagicLink.getEmail());
        }
        if (inviteMagicLink.getExpiresAt().isBefore(LocalDateTime.now())) {
            return new ValidationResult(ValidationStatus.EXPIRED, inviteMagicLink.getEmail());
        }
        return new ValidationResult(ValidationStatus.VALID, inviteMagicLink.getEmail());
    }

    @Transactional
    public String consumeInviteMagicLink(String token) {
        String tokenHash = hashToken(token);
        InviteMagicLink inviteMagicLink = inviteMagicLinkRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("This invite link is invalid."));

        if (inviteMagicLink.getUsedAt() != null) {
            throw new IllegalArgumentException("This invite link has already been used.");
        }
        if (inviteMagicLink.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This invite link has expired. Please request a new one.");
        }

        inviteMagicLink.setUsedAt(LocalDateTime.now());
        // Mark as immediately stale so cleanup can delete it on next pass.
        inviteMagicLink.setExpiresAt(LocalDateTime.now());
        inviteMagicLinkRepository.save(inviteMagicLink);
        return inviteMagicLink.getEmail();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.isEmpty()) {
            throw new IllegalArgumentException("Invite link token is required.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalizedToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 hashing is not available.", e);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }
}
