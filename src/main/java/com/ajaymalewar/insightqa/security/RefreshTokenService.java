package com.ajaymalewar.insightqa.security;

import com.ajaymalewar.insightqa.model.RefreshToken;
import com.ajaymalewar.insightqa.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public RefreshToken createRefreshToken(String username) {
        // Invalidate any previous refresh token for this user before issuing a new one.
        refreshTokenRepository.deleteByUsername(username);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                username,
                Instant.now().plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS)
        );

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Refresh token created for user: {}, expires: {}", username, saved.getExpiryDate());
        return saved;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public boolean isExpired(RefreshToken refreshToken) {
        boolean expired = refreshToken.getExpiryDate().isBefore(Instant.now());
        if (expired) {
            log.warn("Refresh token expired for user: {}", refreshToken.getUsername());
        }
        return expired;
    }

    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
        log.info("Refresh tokens revoked for user: {}", username);
    }
}