package com.restaurant.admin.repository;

import com.restaurant.admin.model.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, Long> {

    Optional<RememberMeToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(Instant time);
}
