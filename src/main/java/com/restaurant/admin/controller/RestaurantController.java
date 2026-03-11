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

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        if (e.isEmpty()) return null;
        return e.toLowerCase();
    }

    /**
     * Resolve email from Authentication (handles OIDC/OAuth2 and form login)
     */
    private String resolveEmail(Authentication auth) {
        if (auth == null) return null;

        Object principal = auth.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            return normalizeEmail(oidcUser.getEmail());
        }

        if (principal instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttributes().get("email");
            return normalizeEmail(email != null ? email.toString() : null);
        }

        return normalizeEmail(auth.getName());
    }

    /**
     * Resolve email from Principal (works for google + password)
     */
    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof Authentication auth) return resolveEmail(auth);
        return normalizeEmail(principal.getName());
    }

    private SimpleUser getCurrentUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) return null;

        SimpleUser user = userService.findByEmail(email);
        if (user == null && principal != null) {
            // fallback in case DB saved email with different case
            user = userService.findByEmail(principal.getName());
        }
        return user;
    }

    // GET /restaurant/setup → show setup form
    @GetMapping("/restaurant/setup")
    public String setupPage(Principal principal) {
        if (principal == null) return "redirect:/login";
        return "restaurant-setup";
    }

    // POST /restaurant/setup → handle form submission
    @PostMapping("/restaurant/setup")
    @ResponseBody
    public ResponseEntity<?> handleSetup(
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantType") String restaurantType,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "logoUpload", required = false) MultipartFile logoUpload,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

                MultipartFile effectiveLogo = (logo != null && !logo.isEmpty()) ? logo : logoUpload;

                Restaurant restaurant = restaurantService.setupRestaurant(
                    user.getId(), restaurantName, restaurantType, effectiveLogo);

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

    // GET /api/restaurant/me → used by menu preview
    @GetMapping("/api/restaurant/me")
    @ResponseBody
    public ResponseEntity<?> getMyRestaurant(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));

            String bg = restaurant.getMenuBackgroundColor();
            String safeBg = (bg == null || bg.isBlank()) ? "" : ColorContrastUtil.normalizeHex(bg);
            String text = safeBg.isBlank() ? "" : ColorContrastUtil.bestTextColor(safeBg);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", restaurant.getId());
            dto.put("restaurantName", restaurant.getRestaurantName());
            dto.put("restaurantType", restaurant.getRestaurantType());
            dto.put("logoPath", restaurantService.toPublicLogoUrl(restaurant.getLogoPath()));
            dto.put("menuBackgroundColor", safeBg);
            dto.put("menuTextColor", text);

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // PUT /api/restaurant/me/theme → save menu background color
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
                // fallback
                user = userService.findByEmail(auth.getName());
            }
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

    // Public endpoint for QR / non-authenticated menu access
    @GetMapping("/api/public/restaurants/{id}")
    @ResponseBody
    public ResponseEntity<?> getRestaurantPublic(@PathVariable("id") Long restaurantId) {
        try {
            Restaurant restaurant = restaurantService.getRestaurantById(restaurantId)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));

            String bg = restaurant.getMenuBackgroundColor();
            String safeBg = (bg == null || bg.isBlank()) ? "" : ColorContrastUtil.normalizeHex(bg);
            String text = safeBg.isBlank() ? "" : ColorContrastUtil.bestTextColor(safeBg);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", restaurant.getId());
            dto.put("restaurantName", restaurant.getRestaurantName());
            dto.put("restaurantType", restaurant.getRestaurantType());
            dto.put("logoPath", restaurantService.toPublicLogoUrl(restaurant.getLogoPath()));
            dto.put("menuBackgroundColor", safeBg);
            dto.put("menuTextColor", text);

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}   