package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import com.restaurant.admin.model.User;
import com.restaurant.admin.service.UserService;
import com.restaurant.admin.dto.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody User user) {
        try {
            User savedUser = userService.registerUser(user);
            ApiResponse<User> response = new ApiResponse<>(true, "User registered successfully", savedUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            ApiResponse<?> response = new ApiResponse<>(false, e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
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
}
