package com.restaurant.admin.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.restaurant.admin.dto.LoginRequest;
import com.restaurant.admin.model.User;
import com.restaurant.admin.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // =========================
    // REGISTER
    // =========================
    public boolean registerUser(String email, String rawPassword) {

        if (userRepository.existsByEmail(email)) {
            return false;
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setActive(true); // IMPORTANT (fixes DB error)

        userRepository.save(user);
        return true;
    }
    // =========================
    // LOGIN/AUTHENTICATE
    // =========================
    public String loginUser(LoginRequest loginRequest) {
        boolean isAuthenticated = authenticateUser(loginRequest.getEmail(), loginRequest.getPassword());
    
        if (isAuthenticated) {
            return "Login successful";
        } else {
            return "Invalid email or password";
        }
    }

    // =========================
    // GET ALL USERS
    // =========================
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // =========================
    // AUTHENTICATE
    // =========================
    public boolean authenticateUser(String email, String rawPassword) {

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            return passwordEncoder.matches(
                    rawPassword,
                    userOpt.get().getPassword()
            );
        }

        return false;
    }
}
