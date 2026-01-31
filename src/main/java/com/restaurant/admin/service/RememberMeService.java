package com.restaurant.admin.service;

import com.restaurant.admin.model.RememberMeToken;
import com.restaurant.admin.repository.RememberMeTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class RememberMeService {

    private static final String COOKIE_NAME = "REMEMBER_ME";
    private static final Duration REMEMBER_ME_DURATION = Duration.ofDays(30);

    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final SecureRandom secureRandom;

    public RememberMeService(RememberMeTokenRepository rememberMeTokenRepository) {
        this.rememberMeTokenRepository = rememberMeTokenRepository;
        this.secureRandom = new SecureRandom();
    }

    public String getCookieName() {
        return COOKIE_NAME;
    }

    public Duration getRememberMeDuration() {
        return REMEMBER_ME_DURATION;
    }

    public String createAndStoreToken(Long userId) {
        // Create random token (raw)
        String rawToken = generateToken();

        // Store only hash in DB
        String tokenHash = sha256Hex(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(REMEMBER_ME_DURATION);

        // Optional: keep only one token per user (simple)
        rememberMeTokenRepository.deleteByUserId(userId);

        RememberMeToken record = new RememberMeToken(userId, tokenHash, expiresAt, now);
        rememberMeTokenRepository.save(record);

        return rawToken; // raw token goes to cookie
    }

    public Optional<Long> validateTokenAndGetUserId(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return Optional.empty();
        }

        String tokenHash = sha256Hex(rawToken);
        Optional<RememberMeToken> recordOpt = rememberMeTokenRepository.findByTokenHash(tokenHash);

        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }

        RememberMeToken record = recordOpt.get();
        Instant now = Instant.now();

        if (record.getExpiresAt().isBefore(now)) {
            // Token expired -> delete it
            rememberMeTokenRepository.delete(record);
            return Optional.empty();
        }

        return Optional.of(record.getUserId());
    }

    public void revokeTokensForUser(Long userId) {
        rememberMeTokenRepository.deleteByUserId(userId);
    }

    public void cleanupExpiredTokens() {
        rememberMeTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hashed.length; i++) {
                sb.append(String.format("%02x", hashed[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }
}
