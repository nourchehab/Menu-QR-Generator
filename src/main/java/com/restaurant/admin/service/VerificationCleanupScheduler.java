package com.restaurant.admin.service;


import com.restaurant.admin.service.EmailVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class VerificationCleanupScheduler {

    @Autowired
    private EmailVerificationService emailVerificationService;

    /**
     * Clean up expired verification codes every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void cleanupExpiredCodes() {
        emailVerificationService.cleanupExpiredCodes();
    }
}