package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.restaurant.admin.service.UserService;

import org.springframework.ui.Model;

@Controller
public class PageController {

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam String email,
        @RequestParam String password,
        Model model) {
        boolean isAuthenticated = userService.authenticateUser(email, password);
            
        if (isAuthenticated) {
            return "redirect:/dashboard";
        } else {
            model.addAttribute("error", "Invalid email or password");
            return "login";
        }
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
