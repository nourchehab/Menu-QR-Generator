package com.restaurant.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;

import java.util.List;
import java.util.Optional;

import static com.restaurant.admin.util.EmailUtil.normalize;

@Service
public class SimpleUserService {

    @Autowired
    private SimpleUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public boolean registerUser(String email, String rawPassword) {
        String normEmail = normalize(email);

        Optional<SimpleUser> existingOpt = userRepository.findByEmail(normEmail);

        if (existingOpt.isPresent()) {
            SimpleUser existing = existingOpt.get();

            // ✅ If user was created via Google (no local password yet), "signup" becomes "set password"
            if (!existing.isPasswordSet()) {
                existing.setPassword(passwordEncoder.encode(rawPassword));
                existing.setPasswordSet(true);
                userRepository.save(existing);
                return true;
            }

            // already exists and has password -> real duplicate
            return false;
        }

        SimpleUser user = new SimpleUser();
        user.setEmail(normEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPasswordSet(true);
        user.setRestaurantSetupComplete(false);

        userRepository.save(user);
        return true;
    }

    public LoginResult authenticateUser(String email, String rawPassword) {
        String normEmail = normalize(email);

        Optional<SimpleUser> userOpt = userRepository.findByEmail(normEmail);
        if (userOpt.isEmpty()) return LoginResult.EMAIL_NOT_FOUND;

        SimpleUser user = userOpt.get();

        // if created by Google and never set password, they can’t use password login yet
        if (!user.isPasswordSet()) return LoginResult.WRONG_PASSWORD;

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            return LoginResult.WRONG_PASSWORD;
        }

        return LoginResult.SUCCESS;
    }

    public List<SimpleUser> getAllUsers() {
        return userRepository.findAll();
    }
}
