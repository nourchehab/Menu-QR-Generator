package com.restaurant.admin.controller;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.util.ColorContrastUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;

    /**
     * Returns the user's email for BOTH:
     * - Form login (auth.getName() is usually email)
     * - Google OAuth2/OIDC (email is inside principal attributes)
     */
    private String resolveEmail(Authentication auth) {
        if (auth == null) return null;

        Object principal = auth.getPrincipal();

        // OIDC (Google OpenID Connect)
        if (principal instanceof OidcUser oidcUser) {
            String email = oidcUser.getEmail();
            return (email != null && !email.isBlank()) ? email : null;
        }

        // OAuth2User fallback
        if (principal instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttributes().get("email");
            return email != null ? email.toString() : null;
        }

        // Form login fallback
        String name = auth.getName();
        return (name != null && !name.isBlank()) ? name : null;
    }

    // ---------------------------------------------------------------
    // GET /restaurant/setup  →  show the setup form page
    // ---------------------------------------------------------------
    @GetMapping("/restaurant/setup")
    public String setupPage(Authentication auth) {
        String email = resolveEmail(auth);
        if (email == null) {
            return "redirect:/login";
        }
        return "restaurant-setup"; // your Thymeleaf template
    }

    // ---------------------------------------------------------------
    // POST /api/restaurant/setup  →  handle form submission (JSON/API)
    // Changed from /restaurant/setup to avoid conflict with form POST handler
    // ---------------------------------------------------------------
    @PostMapping("/api/restaurant/setup")
    @ResponseBody
    public ResponseEntity<?> handleSetup(
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantType") String restaurantType,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            Authentication auth) {

        String email = resolveEmail(auth);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = userService.findByEmail(email);
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
    public ResponseEntity<?> getMyRestaurant(Authentication auth) {
        String email = resolveEmail(auth);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));

            String bg = restaurant.getMenuBackgroundColor();
            String safeBg = (bg == null || bg.isBlank()) ? "" : ColorContrastUtil.normalizeHex(bg);
            String text = safeBg.isBlank() ? "" : ColorContrastUtil.bestTextColor(safeBg);

            // Return a safe DTO-style map (avoids Jackson lazy-load issues with @ManyToOne)
            return ResponseEntity.ok(Map.of(
                    "id", restaurant.getId(),
                    "restaurantName", restaurant.getRestaurantName(),
                    "restaurantType", restaurant.getRestaurantType(),
                    "logoPath", restaurant.getLogoPath() != null ? restaurant.getLogoPath() : "",
                    "menuBackgroundColor", safeBg,
                    "menuTextColor", text
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // PUT /api/restaurant/me/theme  →  save menu background color
    // ---------------------------------------------------------------
    @PutMapping("/api/restaurant/me/theme")
    @ResponseBody
    public ResponseEntity<?> updateMyTheme(@RequestBody Map<String, String> body, Authentication auth) {
        String email = resolveEmail(auth);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            String hex = body.get("menuBackgroundColor");
            Restaurant updated = restaurantService.updateMenuBackgroundColor(user, hex);

            String safeBg = ColorContrastUtil.normalizeHex(updated.getMenuBackgroundColor());
            String text = ColorContrastUtil.bestTextColor(safeBg);
            double contrast = ColorContrastUtil.contrastRatio(safeBg, text);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "menuBackgroundColor", safeBg,
                    "menuTextColor", text,
                    "contrastRatio", contrast
            ));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Public endpoints for QR / non-authenticated menu access
    // ---------------------------------------------------------------
    @GetMapping("/api/public/restaurants/{id}")
    @ResponseBody
    public ResponseEntity<?> getRestaurantPublic(@PathVariable("id") Long restaurantId) {
        try {
            Restaurant restaurant = restaurantService.getRestaurantById(restaurantId)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));

            String bg = restaurant.getMenuBackgroundColor();
            String safeBg = (bg == null || bg.isBlank()) ? "" : ColorContrastUtil.normalizeHex(bg);
            String text = safeBg.isBlank() ? "" : ColorContrastUtil.bestTextColor(safeBg);

            return ResponseEntity.ok(Map.of(
                    "id", restaurant.getId(),
                    "restaurantName", restaurant.getRestaurantName(),
                    "restaurantType", restaurant.getRestaurantType(),
                    "logoPath", restaurant.getLogoPath() != null ? restaurant.getLogoPath() : "",
                    "menuBackgroundColor", safeBg,
                    "menuTextColor", text
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}