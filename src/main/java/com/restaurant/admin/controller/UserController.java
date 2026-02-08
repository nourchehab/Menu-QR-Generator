package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.dto.ApiResponse;

import java.util.List;

@Controller
@RequestMapping("/auth")
public class UserController {

    @Autowired
    private SimpleUserService userService;

    // =========================
    // REST REGISTER
    // =========================
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody SimpleUser user) {
        boolean success = userService.registerUser(user.getEmail(), user.getPassword());

        if (success) {
            ApiResponse<SimpleUser> response = new ApiResponse<>(true, "User registered successfully", user);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            ApiResponse<?> response = new ApiResponse<>(false, "Email already exists");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password) {

        boolean success = userService.registerUser(email, password);

        if (success) {
            return "redirect:/login";
        } else {
            return "redirect:/signup?error=email";
        }
    }

    /*@GetMapping("/login")
    public String loginPage() {
        return "login";
    }*/

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Backend is running!", null));
    }

    @GetMapping
    public List<SimpleUser> getAllUsers() {
        return userService.getAllUsers();
    }
}
