package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import com.restaurant.admin.model.User;

import com.restaurant.admin.service.UserService;
import com.restaurant.admin.dto.ApiResponse;

@Controller
@RequestMapping("/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody User user) {
        try {
            boolean success = userService.registerUser(user.getEmail(), user.getPassword());
            if (success) {
                ApiResponse<User> response = new ApiResponse<>(true, "User registered successfully", user);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                ApiResponse<?> response = new ApiResponse<>(false, "Email already exists");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (RuntimeException e) {
            ApiResponse<?> response = new ApiResponse<>(false, e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

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

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        // For backward compatibility
        return registerUser(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody com.restaurant.admin.dto.LoginRequest loginRequest) {
        String result  = userService.loginUser(loginRequest);

        if (result.contains("successful")){
            ApiResponse<String> response = new ApiResponse<>(true, "Login successful", result);
            return ResponseEntity.ok(response);
        } else {
            ApiResponse<?> response = new ApiResponse<>(false, result);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is running! Database connection OK.";
    }

    // =========================
    // SHOW LOGIN PAGE
    // =========================
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
