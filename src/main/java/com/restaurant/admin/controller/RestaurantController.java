package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.service.BranchService;
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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class RestaurantController {

    @Autowired private RestaurantService restaurantService;
    @Autowired private SimpleUserService  userService;
    @Autowired private BranchRepository  branchRepository;
    @Autowired private BranchService     branchService;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        return e.isEmpty() ? null : e.toLowerCase();
    }

    private String resolveEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidcUser)     return normalizeEmail(oidcUser.getEmail());
        if (principal instanceof OAuth2User oauth2User) {
            Object email = oauth2User.getAttributes().get("email");
            return normalizeEmail(email != null ? email.toString() : null);
        }
        return normalizeEmail(auth.getName());
    }

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof Authentication auth) return resolveEmail(auth);
        return normalizeEmail(principal.getName());
    }

    private SimpleUser getCurrentUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) return null;
        SimpleUser user = userService.findByEmail(email);
        if (user == null && principal != null) user = userService.findByEmail(principal.getName());
        return user;
    }

    // ── Setup pages ───────────────────────────────────────────────────────────

    @GetMapping("/restaurant/setup")
    public String setupPage(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) {
            return "redirect:/login";
        }

        if (user.isRestaurantSetupComplete() && restaurantService.userHasRestaurant(user)) {
            return "redirect:/restaurants";
        }

        model.addAttribute("formAction", "/restaurant/setup");
        return "restaurant-details";
    }

    @PostMapping("/restaurant/setup")
    @ResponseBody
    public ResponseEntity<?> handleSetup(
            @RequestParam("restaurantName") String restaurantName,
            @RequestParam("restaurantType") String restaurantType,
            @RequestParam(value = "logo",       required = false) MultipartFile logo,
            @RequestParam(value = "logoUpload", required = false) MultipartFile logoUpload,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            if (user.isRestaurantSetupComplete() && restaurantService.userHasRestaurant(user)) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Restaurant already exists",
                        "redirectUrl", "/restaurants"
                ));
            }

            MultipartFile effectiveLogo = (logo != null && !logo.isEmpty()) ? logo : logoUpload;
            Restaurant restaurant = restaurantService.setupRestaurant(
                    user.getId(), restaurantName, restaurantType, effectiveLogo);

            return ResponseEntity.ok(Map.of(
                    "success",      true,
                    "message",      "Restaurant saved successfully",
                    "restaurantId", restaurant.getId(),
                    "redirectUrl",  "/restaurants"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Authenticated restaurant info (legacy — used by old menu preview) ─────

    /**
     * GET /api/restaurant/me
     * Returns the user's most-recent restaurant. Kept for backwards compatibility.
     * Prefer /api/restaurant/branch/{branchId} for branch-scoped pages.
     */
    @GetMapping("/api/restaurant/me")
    @ResponseBody
    public ResponseEntity<?> getMyRestaurant(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));

            return ResponseEntity.ok(buildRestaurantDto(restaurant));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/restaurant/branch/{branchId}
     * ✅ Branch-scoped — returns the restaurant info for a specific branch.
     * Used by menu preview, theme, and QR pages when branchId is present.
     * Public — no auth required (menu preview is public).
     */
    @GetMapping("/api/restaurant/branch/{branchId}")
    @ResponseBody
    public ResponseEntity<?> getRestaurantByBranch(@PathVariable Long branchId) {
        try {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found"));

            Restaurant restaurant = branch.getRestaurant();
            Map<String, Object> dto = buildRestaurantDto(restaurant);
            // Also expose branch info so the menu preview can show the branch name
            dto.put("branchId",   branch.getId());
            dto.put("branchName", branch.getBranchName());
            dto.put("isMainBranch", branch.isMainBranch());

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    /**
     * PUT /api/restaurant/me/theme
     * Updates background colour for the user's most-recent restaurant.
     */
    @PutMapping("/api/restaurant/me/theme")
    @ResponseBody
    public ResponseEntity<?> updateMyTheme(@RequestBody Map<String, String> body, Authentication auth) {
        String email = resolveEmail(auth);
        if (email == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        try {
            SimpleUser user = userService.findByEmail(email);
            if (user == null) user = userService.findByEmail(auth.getName());
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            String hex = body.get("menuBackgroundColor");
            Restaurant updated = restaurantService.updateMenuBackgroundColor(user, hex);
            return ResponseEntity.ok(buildThemeResponse(updated));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/restaurant/branch/{branchId}/theme
     * ✅ Branch-scoped theme update — updates the restaurant's colour (shared by all branches).
     */
    @PutMapping("/api/restaurant/branch/{branchId}/theme")
    @ResponseBody
    public ResponseEntity<?> updateBranchTheme(
            @PathVariable Long branchId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String email = resolveEmail(auth);
        if (email == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        try {
            SimpleUser user = userService.findByEmail(email);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            // Verify the branch belongs to this user
            Branch branch = branchService.getBranchForUser(user, branchId);
            String hex = body.get("menuBackgroundColor");

            // Theme is stored on the restaurant level (shared across all branches)
            Restaurant restaurant = branch.getRestaurant();
            String safe = (hex == null || hex.isBlank()) ? "" : ColorContrastUtil.normalizeHex(hex);
            restaurant.setMenuBackgroundColor(safe);
            // Save via service to keep things clean
            Restaurant updated = restaurantService.updateMenuBackgroundColor(user, hex);

            return ResponseEntity.ok(buildThemeResponse(updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Public restaurant info ────────────────────────────────────────────────

    @GetMapping("/api/public/restaurants/{id}")
    @ResponseBody
    public ResponseEntity<?> getRestaurantPublic(@PathVariable("id") Long restaurantId) {
        try {
            Restaurant restaurant = restaurantService.getRestaurantById(restaurantId)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));
            return ResponseEntity.ok(buildRestaurantDto(restaurant));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildRestaurantDto(Restaurant restaurant) {
        String bg     = restaurant.getMenuBackgroundColor();
        String safeBg = (bg == null || bg.isBlank()) ? "" : ColorContrastUtil.normalizeHex(bg);
        String text   = safeBg.isBlank() ? "" : ColorContrastUtil.bestTextColor(safeBg);

        Map<String, Object> dto = new HashMap<>();
        dto.put("id",                    restaurant.getId());
        dto.put("restaurantName",        restaurant.getRestaurantName());
        dto.put("restaurantType",        restaurant.getRestaurantType());
        dto.put("logoPath",              restaurantService.toPublicLogoUrl(restaurant.getLogoPath()));
        dto.put("menuBackgroundColor",   safeBg);
        dto.put("menuTextColor",         text);
        return dto;
    }

    private Map<String, Object> buildThemeResponse(Restaurant updated) {
        String safeBg   = ColorContrastUtil.normalizeHex(updated.getMenuBackgroundColor());
        String text     = ColorContrastUtil.bestTextColor(safeBg);
        double contrast = ColorContrastUtil.contrastRatio(safeBg, text);
        return Map.of(
                "success",              true,
                "menuBackgroundColor",  safeBg,
                "menuTextColor",        text,
                "contrastRatio",        contrast
        );
    }
}
