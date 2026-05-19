package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.RefreshToken;
import com.anushibinj.veemailer.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.auth.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Value("${app.auth.allow-multiple-sessions}")
    private boolean allowMultipleSessions;

    @Transactional
    public RefreshToken createRefreshToken(AppUser user) {
        // If single session enforcement, revoke all existing tokens
        if (!allowMultipleSessions) {
            refreshTokenRepository.revokeAllByUser(user);
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByTokenAndRevokedFalse(token);
    }

    public boolean isTokenExpired(RefreshToken token) {
        return token.getExpiresAt().isBefore(LocalDateTime.now());
    }

    @Transactional
    public void revokeToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAllUserTokens(AppUser user) {
        refreshTokenRepository.revokeAllByUser(user);
    }
}
