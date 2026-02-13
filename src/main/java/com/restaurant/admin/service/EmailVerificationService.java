package com.restaurant.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {

    @Autowired
    private JavaMailSender mailSender;

    // Store verification codes with email as key
    // In production, use Redis or database instead
    private Map<String, VerificationData> verificationCodes = new ConcurrentHashMap<>();

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 10;

    public static class VerificationData {
        private String code;
        private LocalDateTime expiryTime;
        private int attempts;

        public VerificationData(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
            this.attempts = 0;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }

        public int getAttempts() {
            return attempts;
        }

        public void incrementAttempts() {
            this.attempts++;
        }
    }

    /**
     * Generate a random 6-digit verification code
     */
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Send verification code to email for signup
     */
    public void sendSignupVerificationCode(String email) {
        // Normalize email (trim and lowercase)
        String normalizedEmail = email.trim().toLowerCase();
        
        String code = generateVerificationCode();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES);

        // Store the code with expiry time
        verificationCodes.put(normalizedEmail, new VerificationData(code, expiryTime));

        System.out.println("========================================");
        System.out.println("EMAIL VERIFICATION - CODE SENT");
        System.out.println("========================================");
        System.out.println("Email: '" + normalizedEmail + "'");
        System.out.println("Code: " + code);
        System.out.println("Expiry: " + expiryTime);
        System.out.println("Storage size: " + verificationCodes.size());
        System.out.println("All stored emails: " + verificationCodes.keySet());
        System.out.println("========================================");

        // Send email
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(normalizedEmail);
            message.setSubject("FlavorFrame - Verify Your Email");
            message.setText(
                    "Welcome to FlavorFrame!\n\n" +
                    "Your verification code is: " + code + "\n\n" +
                    "This code will expire in " + CODE_EXPIRY_MINUTES + " minutes.\n\n" +
                    "If you didn't request this code, please ignore this email.\n\n" +
                    "Best regards,\n" +
                    "The FlavorFrame Team"
            );

            mailSender.send(message);
            System.out.println("✓ Email sent successfully to: " + normalizedEmail);
        } catch (Exception e) {
            System.err.println("✗ ERROR sending email: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    /**
     * Verify the code entered by the user
     */
    public boolean verifyCode(String email, String code) {
        // Normalize inputs (trim and lowercase email, trim code)
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedCode = code.trim();
        
        System.out.println("========================================");
        System.out.println("EMAIL VERIFICATION - VERIFYING CODE");
        System.out.println("========================================");
        System.out.println("Email received: '" + email + "'");
        System.out.println("Email normalized: '" + normalizedEmail + "'");
        System.out.println("Code received: '" + code + "'");
        System.out.println("Code normalized: '" + normalizedCode + "'");
        System.out.println("Storage size: " + verificationCodes.size());
        System.out.println("All stored emails: " + verificationCodes.keySet());
        
        VerificationData data = verificationCodes.get(normalizedEmail);

        if (data == null) {
            System.err.println("✗ ERROR: No code found for email: '" + normalizedEmail + "'");
            System.out.println("Available emails in storage:");
            for (String key : verificationCodes.keySet()) {
                System.out.println("  - '" + key + "'");
            }
            System.out.println("========================================");
            return false;
        }
        
        System.out.println("Stored code: '" + data.getCode() + "'");
        System.out.println("Expiry time: " + data.getExpiryTime());
        System.out.println("Current time: " + LocalDateTime.now());
        System.out.println("Attempts so far: " + data.getAttempts());

        // Check if code has expired
        if (LocalDateTime.now().isAfter(data.getExpiryTime())) {
            System.err.println("✗ ERROR: Code has expired");
            verificationCodes.remove(normalizedEmail);
            System.out.println("========================================");
            return false;
        }

        // Check if too many attempts
        if (data.getAttempts() >= 5) {
            System.err.println("✗ ERROR: Too many attempts (5+)");
            verificationCodes.remove(normalizedEmail);
            System.out.println("========================================");
            return false;
        }

        data.incrementAttempts();
        System.out.println("Attempt #" + data.getAttempts());

        // Verify the code
        boolean isValid = data.getCode().equals(normalizedCode);
        
        if (isValid) {
            System.out.println("✓ SUCCESS: Code verified!");
            verificationCodes.remove(normalizedEmail);
            System.out.println("Code removed from storage");
        } else {
            System.err.println("✗ ERROR: Code does not match");
            System.out.println("Expected: '" + data.getCode() + "'");
            System.out.println("Received: '" + normalizedCode + "'");
        }
        
        System.out.println("========================================");
        return isValid;
    }

    /**
     * Resend verification code
     */
    public void resendVerificationCode(String email) {
        // Normalize email
        String normalizedEmail = email.trim().toLowerCase();
        
        System.out.println("Resending verification code to: " + normalizedEmail);
        // Remove old code and send new one
        verificationCodes.remove(normalizedEmail);
        sendSignupVerificationCode(normalizedEmail);
    }

    /**
     * Check if verification code exists for email
     */
    public boolean hasVerificationCode(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        return verificationCodes.containsKey(normalizedEmail);
    }

    /**
     * Clean up expired codes (call this periodically)
     */
    public void cleanupExpiredCodes() {
        int beforeSize = verificationCodes.size();
        verificationCodes.entrySet().removeIf(entry ->
                LocalDateTime.now().isAfter(entry.getValue().getExpiryTime())
        );
        int afterSize = verificationCodes.size();
        int removed = beforeSize - afterSize;
        
        if (removed > 0) {
            System.out.println("Cleaned up " + removed + " expired verification codes");
        }
    }
}