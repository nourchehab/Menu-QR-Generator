package com.restaurant.admin.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/request-otp")
    public void sendOtp(@RequestParam String email) {

        String otp = String.format("%06d", new Random().nextInt(999999));

        OtpStore.saveOtp(email, otp);
        emailService.sendOtp(email, otp);
    }

    @PostMapping("/verify-otp")
public boolean verifyOtp(
        @RequestParam String email,
        @RequestParam String otp) {

    if (!otp.matches("\\d{6}")) {
        return false;
    }

    return OtpStore.verifyOtp(email, otp);
}

    @PostMapping("/reset-password")
    public void resetPassword(
            @RequestParam String email,
            @RequestParam String newPassword) {

        // password update logic later
        OtpStore.removeOtp(email);
    }
}