package com.restaurant.admin.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.restaurant.admin.repository.UserRepository;

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
}