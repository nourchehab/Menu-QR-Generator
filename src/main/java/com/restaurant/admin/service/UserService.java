package com.restaurant.admin.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.restaurant.admin.repository.UserRepository;
import com.restaurant.admin.model.User;
import com.restaurant.admin.dto.LoginRequest;


@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    
    public com.restaurant.admin.model.User createUser(final com.restaurant.admin.model.User user){
        return userRepository.save(user);
    }

    public List<com.restaurant.admin.model.User> getAllUsers(){
        return userRepository.findAll();
    }

    public java.util.Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public String loginUser(LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getEmail()).orElse(null);

        if (user == null) {
            return "User not found!";
        }

        // TEMP: plain text password check (we will upgrade this after)
        if (loginRequest.getPassword().equals(user.getPassword())) {
            return "Login successful! Welcome " + user.getUsername();
        } else {
            return "Invalid password!";
        }
    }



}