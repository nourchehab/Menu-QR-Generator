package com.restaurant.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;
@RestController
public class PublicMenuController {

    @GetMapping("/menu/{restaurantId}")
    public String viewPublicMenu(@PathVariable("restaurantId") Long restaurantId) {
        return "Public menu placeholder for restaurantId = " + restaurantId;
    }
     @GetMapping("/m/{restaurantId}")
    public String publicMenu(@PathVariable Long restaurantId, Model model) {
        model.addAttribute("publicMode", true);
        model.addAttribute("restaurantId", restaurantId);
        return "menu-preview";
    }
}
