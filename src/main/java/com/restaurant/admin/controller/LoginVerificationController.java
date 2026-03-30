package com.restaurant.admin.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.EmailVerificationService;
import com.restaurant.admin.service.SimpleUserService;

/**
 * Controller for handling login with email verification.
 * Provides API endpoints for the two-step login process.
 */
@RestController
@RequestMapping("/api/login")
public class LoginVerificationController {

    @Autowired
    private SimpleUserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * Step 1: Verify user credentials (email + password)
     * Returns 200 if valid, 401 if invalid
     */
    @PostMapping("/verify-credentials")
    public ResponseEntity<String> verifyCredentials(@RequestBody LoginRequest request) {
        
        // Check if user exists
        if (!userService.emailExists(request.getEmail())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        // Verify password
        var user = userService.findByEmail(request.getEmail());
        if (user == null) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        return ResponseEntity.ok("Credentials valid");
    }

    /**
     * Step 2: Send verification code to email  
     */
    @PostMapping("/send-code")
    public ResponseEntity<String> sendVerificationCode(@RequestBody CodeRequest request) {
        
        try {
            emailVerificationService.sendSignupVerificationCode(request.getEmail());
            return ResponseEntity.ok("Code sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send code");
        }
    }

    /**
     * Step 3: Verify the code
     * If valid, returns success and frontend will complete login
     */
    @PostMapping("/verify-code")
    public ResponseEntity<Map<String, String>> verifyCode(@RequestBody VerifyRequest request) {
        
        boolean valid = emailVerificationService.verifyCode(request.getEmail(), request.getCode());
        
        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired code"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    // Request DTOs
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class CodeRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class VerifyRequest {
        private String email;
        private String code;
        private boolean rememberMe;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public boolean isRememberMe() {
            return rememberMe;
        }

        public void setRememberMe(boolean rememberMe) {
            this.rememberMe = rememberMe;
        }
    }
}