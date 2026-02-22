package com.restaurant.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicMenuController {

    @GetMapping("/menu/{restaurantId}")
    public String viewPublicMenu(@PathVariable("restaurantId") Long restaurantId) {
        return "Public menu placeholder for restaurantId = " + restaurantId;
    }
}
