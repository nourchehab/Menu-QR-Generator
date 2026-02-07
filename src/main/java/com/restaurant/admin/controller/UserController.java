package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.restaurant.admin.service.UserService;

@Controller
@RequestMapping("/auth")
public class UserController {

    @Autowired
    private UserService userService;

    // =========================
    // SHOW SIGNUP PAGE
    // =========================
    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    // =========================
    // HANDLE SIGNUP
    // =========================
    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password,
            Model model) {

        boolean success = userService.registerUser(email, password);

        if (success) {
            return "redirect:/login";
        }

        model.addAttribute("error", "Email already exists");
        return "signup";
    }

    // =========================
    // SHOW LOGIN PAGE
    // =========================
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
