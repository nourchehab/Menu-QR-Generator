package com.restaurant.admin.controller;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/e2e")
public class E2eBootstrapController {

    private final SimpleUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final boolean bootstrapEnabled;

    public E2eBootstrapController(
            SimpleUserService userService,
            PasswordEncoder passwordEncoder,
            @Value("${app.e2e.bootstrap.enabled:false}") boolean bootstrapEnabled
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEnabled = bootstrapEnabled;
    }

    @PostMapping("/bootstrap-user")
    public ResponseEntity<?> bootstrapUser(@RequestBody BootstrapRequest request) {
        if (!bootstrapEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }

        if (request == null || isBlank(request.email) || isBlank(request.password)) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required"));
        }

        SimpleUser existing = userService.findByEmail(request.email);
        if (existing == null) {
            userService.registerUser(request.email, request.password);
        } else {
            existing.setPassword(passwordEncoder.encode(request.password));
            existing.setPasswordSet(true);
            userService.save(existing);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class BootstrapRequest {
        public String email;
        public String password;
    }
}
