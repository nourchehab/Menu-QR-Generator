package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.restaurant.admin.service.SimpleUserService;

@Controller
public class SignupController {

    @Autowired
    private SimpleUserService userService;

    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword) {

        // Passwords must match
        if (!password.equals(confirmPassword)) {
            return "redirect:/signup?error=password";
        }

        // Register user
        boolean success = userService.registerUser(email, password);

        if (!success) {
            return "redirect:/signup?error=email";
        }

        return "redirect:/login";
    }
}
