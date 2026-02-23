package com.restaurant.admin.controller;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.MenuItemService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MenuItemController {

    @Autowired
    private MenuItemService menuItemService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;

    /**
     * Show add menu item form
     */
    @GetMapping("/items/add")
    public String showAddItemForm(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Get user from principal
        String email = principal.getName();
        SimpleUser currentUser = userService.findByEmail(email);

        if (currentUser == null) {
            return "redirect:/login";
        }

        // Check if user has a restaurant
        Restaurant restaurant = restaurantService.getRestaurantByUser(currentUser)
                .orElse(null);

        if (restaurant == null) {
            return "redirect:/restaurant/setup";
        }

        model.addAttribute("restaurant", restaurant);
        return "add-menu-item"; // This maps to your HTML file
    }

    /**
     * ✅ API endpoint to add menu item (uses Principal instead of session)
     */
    @PostMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> addMenuItem(
            @RequestParam("itemName") String itemName,
            @RequestParam("itemPrice") BigDecimal itemPrice,
            @RequestParam("itemDescription") String itemDescription,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photoFile,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            // ✅ Get user from Principal (email)
            String email = principal.getName();
            SimpleUser currentUser = userService.findByEmail(email);

            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found"));
            }

            // Get user's restaurant
            Restaurant restaurant = restaurantService.getRestaurantByUser(currentUser)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));

            // Validate inputs
            if (itemName == null || itemName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Item name is required"));
            }

            if (itemPrice == null || itemPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valid price is required"));
            }

            if (itemDescription == null || itemDescription.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Item description is required"));
            }

            // Add menu item
            MenuItem menuItem = menuItemService.addMenuItem(
                    restaurant.getId(),
                    itemName,
                    itemPrice,
                    itemDescription,
                    photoFile
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Menu item added successfully");
            response.put("itemId", menuItem.getId());
            response.put("itemName", menuItem.getItemName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error adding menu item: " + e.getMessage()));
        }
    }

    /**
     * ✅ FIXED: Get all menu items for current user's restaurant
     * Returns safe DTO JSON (avoids invalid JSON / circular JPA serialization)
     */
    @GetMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> getMenuItems(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            String email = principal.getName();
            SimpleUser currentUser = userService.findByEmail(email);

            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found"));
            }

            Restaurant restaurant = restaurantService.getRestaurantByUser(currentUser)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));

            List<MenuItem> menuItems = menuItemService.getMenuItemsByRestaurant(restaurant.getId());

            // ✅ Return only the fields your menu-preview.html expects
            List<Map<String, Object>> dto = menuItems.stream().map(item -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", item.getId());
                m.put("itemName", item.getItemName());
                m.put("itemDescription", item.getItemDescription());
                m.put("itemPrice", item.getItemPrice());
                m.put("photoPath", item.getPhotoPath() == null ? "" : item.getPhotoPath());
                return m;
            }).toList();

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public menu items endpoint (for QR / non-authenticated access).
     * Returns the same safe DTO structure used by menu-preview.html.
     */
    @GetMapping("/api/public/restaurants/{restaurantId}/items")
    @ResponseBody
    public ResponseEntity<?> getMenuItemsPublic(@PathVariable Long restaurantId) {
        try {
            List<MenuItem> menuItems = menuItemService.getMenuItemsByRestaurant(restaurantId);

            List<Map<String, Object>> dto = menuItems.stream().map(item -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", item.getId());
                m.put("itemName", item.getItemName());
                m.put("itemDescription", item.getItemDescription());
                m.put("itemPrice", item.getItemPrice());
                m.put("photoPath", item.getPhotoPath() == null ? "" : item.getPhotoPath());
                return m;
            }).toList();

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete menu item
     */
    @DeleteMapping("/api/items/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteMenuItem(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            menuItemService.deleteMenuItem(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Item deleted successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}