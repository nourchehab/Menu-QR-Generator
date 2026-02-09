package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Random;
import com.restaurant.admin.service.EmailService;
import com.restaurant.admin.service.OtpStore;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/request-otp")
    public ResponseEntity<?> sendOtp(@RequestParam String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        OtpStore.saveOtp(email, otp);
        emailService.sendOtp(email, otp);
        return ResponseEntity.ok("OTP sent to " + email);
    }

    @PostMapping("/verify-otp")
public boolean verifyOtp(@RequestParam String email, @RequestParam String otp) {
    if (!otp.matches("\\d{6}")) {
        return false;
    }
    return OtpStore.verifyOtp(email, otp);
}

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String email, @RequestParam String newPassword) {
        OtpStore.removeOtp(email);
        return ResponseEntity.ok("Password reset");
    }
}