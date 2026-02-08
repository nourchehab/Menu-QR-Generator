package com.restaurant.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class SimpleUserService {

    @Autowired
    private SimpleUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Register new user
    public boolean registerUser(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            return false;
        }
        SimpleUser user = new SimpleUser();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        return true;
    }

    // Authenticate login
    public boolean authenticateUser(String email, String rawPassword) {
        Optional<SimpleUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return passwordEncoder.matches(rawPassword, userOpt.get().getPassword());
        }
        return false;
    }

    // Get all users
    public List<SimpleUser> getAllUsers() {
        return userRepository.findAll();
    }
}