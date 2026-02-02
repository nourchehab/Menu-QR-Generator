package com.restaurant.admin.controller;

import com.restaurant.admin.dto.LoginRequest;
import com.restaurant.admin.model.User;
import com.restaurant.admin.service.CookieService;
import com.restaurant.admin.service.RememberMeService;
import com.restaurant.admin.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RememberMeService rememberMeService;

    @Autowired
    private CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response
    ) {
        String result = userService.loginUser(loginRequest);

        if (result.contains("successful")) {
            String username = result.replace("Login successful! Welcome ", "");

            User user = userService.findByUsername(username).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Login succeeded but user not found");
            }

            String rawToken = rememberMeService.createAndStoreToken(user.getId());
            int maxAgeSeconds = (int) rememberMeService.getRememberMeDuration().getSeconds();

            cookieService.addHttpOnlyCookie(
                    response,
                    rememberMeService.getCookieName(),
                    rawToken,
                    maxAgeSeconds
            );

            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
}
