package com.restaurant.admin.controller;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.MenuItemService;
import com.restaurant.admin.service.MenuItemImageStorageService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    private MenuItemImageStorageService imageStorageService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;


    // ✅ Works for BOTH password login and Google login
    private String resolveEmail(Principal principal) {
        if (principal == null)
            return null;

        if (principal instanceof Authentication auth) {
            Object p = auth.getPrincipal();

            // Google OIDC
            if (p instanceof OidcUser oidcUser) {
                return normalizeEmail(oidcUser.getEmail());
            }

            // Generic OAuth2
            if (p instanceof OAuth2User oauth2User) {
                Object emailAttr = oauth2User.getAttributes().get("email");
                return normalizeEmail(emailAttr == null ? null : emailAttr.toString());
            }

            // Form login fallback
            return normalizeEmail(auth.getName());
        }

        return normalizeEmail(principal.getName());
    }

    private String normalizeEmail(String email) {
        if (email == null)
            return null;
        String e = email.trim();
        if (e.isEmpty())
            return null;
        return e.toLowerCase();
    }

    private Restaurant getCurrentUsersRestaurant(Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        String email = resolveEmail(principal);
        if (email == null) {
            throw new RuntimeException("User not authenticated");
        }

        SimpleUser currentUser = userService.findByEmail(email);

        // fallback (just in case DB stored email with different case)
        if (currentUser == null) {
            currentUser = userService.findByEmail(principal.getName());
        }

        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }

        return restaurantService.getRestaurantByUser(currentUser)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
    }

    private void assertItemBelongsToRestaurant(MenuItem item, Restaurant restaurant) {
        if (item.getRestaurant() == null ||
                item.getRestaurant().getId() == null ||
                !item.getRestaurant().getId().equals(restaurant.getId())) {
            throw new SecurityException("Not allowed to modify this menu item");
        }
    }

    /**
     * Show add menu item form
     */
    @GetMapping("/items/add")
    public String showAddItemForm(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        String email = resolveEmail(principal);
        if (email == null)
            return "redirect:/login";

        SimpleUser currentUser = userService.findByEmail(email);
        if (currentUser == null) {
            // fallback
            currentUser = userService.findByEmail(principal.getName());
        }

        if (currentUser == null) {
            return "redirect:/login";
        }

        Restaurant restaurant = restaurantService.getRestaurantByUser(currentUser).orElse(null);
        if (restaurant == null) {
            return "redirect:/restaurant/setup";
        }

        model.addAttribute("restaurant", restaurant);
        return "add-menu-item";
    }

    /**
     * Test endpoint for debugging image upload
     */
    @PostMapping("/api/items/test-upload")
    @ResponseBody
    public ResponseEntity<?> testUpload(
            @RequestParam("itemName") String itemName,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photoFile) {
        try {
            System.out.println("Received test upload: " + itemName);
            if (photoFile == null) {
                System.out.println("photoFile is NULL!");
                return ResponseEntity.ok(Map.of("error", "photoFile is null"));
            }
            if (photoFile.isEmpty()) {
                System.out.println("photoFile is EMPTY!");
                return ResponseEntity.ok(Map.of("error", "photoFile is empty"));
            }
            System.out.println("photoFile size: " + photoFile.getSize());
            String url = imageStorageService.storePhoto(photoFile);
            System.out.println("Stored photo at URL: " + url);
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add menu item
     */
    @PostMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> addMenuItem(
            @RequestParam("itemName") String itemName,
            @RequestParam("itemPrice") BigDecimal itemPrice,
            @RequestParam("itemDescription") String itemDescription,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photoFile,
            @RequestParam(value = "category", required = false) String category,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            Restaurant restaurant = getCurrentUsersRestaurant(principal);

            if (itemName == null || itemName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Item name is required"));
            }
            if (itemPrice == null || itemPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid price is required"));
            }
            if (itemDescription == null || itemDescription.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Item description is required"));
            }

            MenuItem menuItem = menuItemService.addMenuItem(
                    restaurant.getId(),
                    itemName,
                    itemPrice,
                    itemDescription,
                    photoFile,
                    category);

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
     * ✅ Get all menu items for current user's restaurant
     * IMPORTANT: must return photoUrl because manageitems.html uses it.
     */
    @GetMapping("/api/items")
    @ResponseBody
    public ResponseEntity<?> getMenuItems(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            Restaurant restaurant = getCurrentUsersRestaurant(principal);

            List<MenuItem> menuItems = menuItemService.getMenuItemsByRestaurant(restaurant.getId());

            List<Map<String, Object>> dto = menuItems.stream().map(item -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", item.getId());
                m.put("itemName", item.getItemName());
                m.put("itemDescription", item.getItemDescription());
                m.put("itemPrice", item.getItemPrice());

                String photoPath = item.getPhotoPath() == null ? "" : item.getPhotoPath();
                m.put("photoPath", photoPath);
                m.put("photoUrl", imageStorageService.toPublicUrl(photoPath)); // ✅ THIS fixes images on manage page
                m.put("thumbUrl", imageStorageService.toThumbPublicUrl(
                        item.getThumbPath() == null ? "" : item.getThumbPath()));
                m.put("category", item.getCategory() == null ? "" : item.getCategory());

                return m;
            }).toList();

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public menu items endpoint (for QR / preview)
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

                String photoPath = item.getPhotoPath() == null ? "" : item.getPhotoPath();
                m.put("photoPath", photoPath);
                m.put("photoUrl", imageStorageService.toPublicUrl(photoPath));
                m.put("thumbUrl", imageStorageService.toThumbPublicUrl(
                        item.getThumbPath() == null ? "" : item.getThumbPath()));
                m.put("category", item.getCategory() == null ? "" : item.getCategory());

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
            Restaurant restaurant = getCurrentUsersRestaurant(principal);
            MenuItem item = menuItemService.getMenuItemById(id);
            assertItemBelongsToRestaurant(item, restaurant);

            menuItemService.deleteMenuItem(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Item deleted successfully"));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update menu item (name/price/description) and optionally replace image
     * manageitems.html expects JSON {success:true,...}
     */
    @PostMapping("/api/items/{id}")
    @ResponseBody
    public ResponseEntity<?> updateMenuItem(
            @PathVariable Long id,
            @RequestParam("itemName") String itemName,
            @RequestParam("itemPrice") BigDecimal itemPrice,
            @RequestParam("itemDescription") String itemDescription,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photoFile,
            @RequestParam(value = "category", required = false) String category,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            Restaurant restaurant = getCurrentUsersRestaurant(principal);
            MenuItem existing = menuItemService.getMenuItemById(id);
            assertItemBelongsToRestaurant(existing, restaurant);

            MenuItem updated = menuItemService.updateMenuItem(id, itemName, itemPrice, itemDescription, photoFile, category);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item updated successfully");
            response.put("photoUrl", imageStorageService.toPublicUrl(updated.getPhotoPath()));

            return ResponseEntity.ok(response);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (IllegalArgumentException iae) {
            // image validation errors land here
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            e.printStackTrace(); // ✅ leave this so you see the real cause in console
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload/replace image only.
     */
    @PostMapping("/api/items/{id}/image")
    @ResponseBody
    public ResponseEntity<?> uploadItemImage(
            @PathVariable Long id,
            @RequestParam("itemPhoto") MultipartFile photoFile,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            Restaurant restaurant = getCurrentUsersRestaurant(principal);
            MenuItem existing = menuItemService.getMenuItemById(id);
            assertItemBelongsToRestaurant(existing, restaurant);

            MenuItem updated = menuItemService.uploadMenuItemImage(id, photoFile);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Image uploaded successfully",
                    "photoUrl", imageStorageService.toPublicUrl(updated.getPhotoPath())));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete image only.
     */
    @DeleteMapping("/api/items/{id}/image")
    @ResponseBody
    public ResponseEntity<?> deleteItemImage(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        try {
            Restaurant restaurant = getCurrentUsersRestaurant(principal);
            MenuItem existing = menuItemService.getMenuItemById(id);
            assertItemBelongsToRestaurant(existing, restaurant);

            menuItemService.deleteMenuItemImage(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Image deleted successfully"));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}