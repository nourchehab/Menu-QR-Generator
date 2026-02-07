package com.restaurant.admin.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.restaurant.admin.model.User;
import com.restaurant.admin.repository.UserRepository;

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
