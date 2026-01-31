package com.restaurant.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import com.restaurant.admin.model.User;
import com.restaurant.admin.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")

public class UserController {

    private UserService userService;
    
    @PostMapping
    public User createUser(@Valid @RequestBody User user){
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getAllUsers(){
        return userService.getAllUsers();
    }

}
