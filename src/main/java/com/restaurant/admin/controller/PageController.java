package com.restaurant.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import java.security.Principal;

@Controller
public class PageController {

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String oauthError,
            @RequestParam(required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }

        if (oauthError != null) {
            model.addAttribute("error", "Google login failed or was canceled. Please try again.");
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }

        return "login";
    }

    // Spring Security handles POST /login

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/enteritems")
    public String enterItems() {
        return "enteritems";
    }

    @GetMapping("/manageitems")
    public String manageItems() {
        return "manageitems";
    }

    @GetMapping("/menu/preview")
    public String menuPreview(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("publicMode", false);
        return "menu-preview";
    }
@GetMapping("/menu/theme")
    public String menuTheme(Principal principal) {
        if (principal == null) return "redirect:/login";
        return "menu-theme";
    }
    @GetMapping("/qr-page")
    public String qrPage() {
        return "qr-page";
    }
}
