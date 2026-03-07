package com.restaurant.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
@Controller
public class PublicMenuController {

  // Canonical public URL — QR code points here
    @GetMapping("/menu/{restaurantId}")
    public String viewPublicMenu(
            @PathVariable Long restaurantId,
            Model model) {
        model.addAttribute("publicMode", true);
        model.addAttribute("restaurantId", restaurantId);
        return "menu-preview";  // resolves to templates/menu-preview.html
    }

    // Keep /m/ as an alias so old QR codes still work
    @GetMapping("/m/{restaurantId}")
    public String publicMenuAlias(
            @PathVariable Long restaurantId,
            Model model) {
        model.addAttribute("publicMode", true);
        model.addAttribute("restaurantId", restaurantId);
        return "menu-preview";
    }
}
