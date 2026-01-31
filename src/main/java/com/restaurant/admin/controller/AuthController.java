package com.restaurant.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String oauth2_error,
            @RequestParam(required = false) String error) {

        if (oauth2_error != null) {
            return "Google login failed or was cancelled ❌";
        }

        if (error != null) {
            return "Login failed: " + error;
        }

        return "Login via /oauth2/authorization/google";
    }
}
