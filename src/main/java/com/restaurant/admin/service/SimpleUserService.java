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
    public LoginResult authenticateUser(String email, String rawPassword) {

    Optional<SimpleUser> userOpt = userRepository.findByEmail(email);

    if (userOpt.isEmpty()) {
        return LoginResult.EMAIL_NOT_FOUND;
    }

    SimpleUser user = userOpt.get();

    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
        return LoginResult.WRONG_PASSWORD;
    }

    return LoginResult.SUCCESS;
}


    // Get all users
    public List<SimpleUser> getAllUsers() {
        return userRepository.findAll();
    }
}