package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Random;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.EmailService;
import com.restaurant.admin.service.OtpStore;
import com.restaurant.admin.service.SimpleUserService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private EmailService emailService;
@Autowired
private SimpleUserService userService;

@Autowired
private PasswordEncoder passwordEncoder;
    @PostMapping("/request-otp")
public ResponseEntity<?> sendOtp(@RequestParam String email) {
    SimpleUser user = userService.findByEmail(email);
    if (user == null) {
        return ResponseEntity.badRequest().body("Email does not exist");
    }

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

    // 1️⃣ Find user
    SimpleUser user = userService.findByEmail(email); // use your service
    if (user == null) {
        return ResponseEntity.badRequest().body("Email does not exist");
    }

    // 2️⃣ Hash the new password
    user.setPassword(passwordEncoder.encode(newPassword));

    // 3️⃣ Save to database
    userService.save(user);

    // 4️⃣ Remove the OTP now that it’s used
    OtpStore.removeOtp(email);

    return ResponseEntity.ok("Password reset successfully");
}
}