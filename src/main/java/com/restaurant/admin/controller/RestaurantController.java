// ===== ADD THIS ENDPOINT to your existing RestaurantController (or create one) =====
// File: src/main/java/com/restaurant/admin/controller/RestaurantController.java
package com.restaurant.admin.controller;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

@Controller
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;

    // ---------------------------------------------------------------
    // GET /restaurant/setup  →  show the setup form page
    // ---------------------------------------------------------------
    @GetMapping("/restaurant/setup")
    public String setupPage(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "restaurant-setup"; // your Thymeleaf template
    }

    // ---------------------------------------------------------------
    // POST /restaurant/setup  →  handle form submission
    // ---------------------------------------------------------------
    @PostMapping("/restaurant/setup")
    @ResponseBody
    public ResponseEntity<?> handleSetup(
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantType") String restaurantType,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = userService.findByEmail(principal.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            Restaurant restaurant = restaurantService.setupRestaurant(
                    user.getId(), restaurantName, restaurantType, logo);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Restaurant saved successfully",
                    "restaurantId", restaurant.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // GET /api/restaurant/me  →  used by the menu preview page
    // Returns current user's restaurant as JSON
    // ---------------------------------------------------------------
    @GetMapping("/api/restaurant/me")
    @ResponseBody
    public ResponseEntity<?> getMyRestaurant(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = userService.findByEmail(principal.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));

            // Return a safe DTO-style map (avoids Jackson lazy-load issues with @ManyToOne)
            return ResponseEntity.ok(Map.of(
                    "id", restaurant.getId(),
                    "restaurantName", restaurant.getRestaurantName(),
                    "restaurantType", restaurant.getRestaurantType(),
                    "logoPath", restaurant.getLogoPath() != null ? restaurant.getLogoPath() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
