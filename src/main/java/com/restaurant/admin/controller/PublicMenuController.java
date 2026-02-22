package com.restaurant.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Public menu page that can be opened via QR code.
 *
 * Example URL: /m/123
 */
@Controller
public class PublicMenuController {

    @GetMapping("/m/{restaurantId}")
    public String publicMenu(@PathVariable Long restaurantId, Model model) {
        model.addAttribute("publicMode", true);
        model.addAttribute("restaurantId", restaurantId);
        return "menu-preview";
    }
}
