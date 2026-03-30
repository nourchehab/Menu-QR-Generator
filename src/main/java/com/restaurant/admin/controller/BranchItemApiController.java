package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.BranchService.EffectiveMenuItem;
import com.restaurant.admin.service.MenuItemService;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class BranchItemApiController {

    @Autowired private BranchService     branchService;
    @Autowired private SimpleUserService userService;
    @Autowired private MenuItemService   menuItemService;
    @Autowired private BranchRepository  branchRepository;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        return e.isEmpty() ? null : e.toLowerCase();
    }

    private String resolveEmail(Authentication auth) {
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof OidcUser   u) return normalizeEmail(u.getEmail());
        if (p instanceof OAuth2User u) {
            Object email = u.getAttributes().get("email");
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

    // ── PUBLIC endpoint — used by QR scan (no auth required) ─────────────────

    /**
     * GET /api/branches/{branchId}
     * Returns branch details including restaurantId.
     * Used by manageitems.html to get restaurant ID for categorization API calls.
     */
    @GetMapping("/api/branches/{branchId}")
    public ResponseEntity<?> getBranchDetails(@PathVariable Long branchId) {
        try {
            Branch branch = branchRepository.findById(branchId)
                    .orElse(null);
            
            if (branch == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Branch not found"));
            }
            
            // Check if restaurant exists
            if (branch.getRestaurant() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Branch has no associated restaurant"));
            }
            
            Map<String, Object> response = Map.of(
                    "id", branch.getId(),
                    "name", branch.getBranchName() != null ? branch.getBranchName() : "",
                    "restaurantId", branch.getRestaurant().getId(),
                    "isMainBranch", branch.isMainBranch(),
                    "data", Map.of(
                            "restaurantId", branch.getRestaurant().getId()
                    )
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in getBranchDetails: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * GET /api/public/branch/{branchId}/items
     * Returns the effective menu for a branch publicly.
     * Called by menu-preview.html when publicMode=true and branchId is set.
     * No authentication required — this is what customers see after scanning QR.
     */
    @GetMapping("/api/public/branch/{branchId}/items")
    public ResponseEntity<?> getPublicBranchItems(@PathVariable Long branchId) {
        try {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            List<EffectiveMenuItem> items = branchService.buildEffectiveMenu(branch);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── AUTHENTICATED endpoints — used by dashboard manage/enter items ────────

    /**
     * GET /api/branch/{branchId}/items
     * Returns items for the branch — requires auth.
     */
    @GetMapping("/api/branch/{branchId}/items")
    public ResponseEntity<?> getItems(@PathVariable Long branchId, Principal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            List<EffectiveMenuItem> items = branchService.getEffectiveMenu(user, branchId);
            return ResponseEntity.ok(items);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/branch/{branchId}/items
     * Add a new item to the branch.
     */
    @PostMapping("/api/branch/{branchId}/items")
    public ResponseEntity<?> addItem(
            @PathVariable Long branchId,
            @RequestParam("itemName")        String name,
            @RequestParam("itemPrice")       Double price,
            @RequestParam("itemDescription") String description,
            @RequestParam(value = "category",  required = false) String category,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photo,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Branch branch = branchService.getBranchForUser(user, branchId);

            if (branch.isMainBranch()) {
                // Main branch → add to real MenuItem table so future branches can snapshot it
                BigDecimal bdPrice = price != null ? BigDecimal.valueOf(price) : BigDecimal.ZERO;
                menuItemService.addMenuItem(branch.getRestaurant().getId(), name, bdPrice,
                        description, photo, category != null ? category : "");
            } else {
                // Other branches → independent snapshot item
                branchService.addBranchOnlyItem(user, branchId, name, description, price,
                        category != null ? category : "");
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Item added"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/branch/{branchId}/items/{itemId}
     * Edit an item.
     */
    @PostMapping("/api/branch/{branchId}/items/{itemId}")
    public ResponseEntity<?> editItem(
            @PathVariable Long branchId,
            @PathVariable Long itemId,
            @RequestParam("itemName")        String name,
            @RequestParam("itemPrice")       Double price,
            @RequestParam("itemDescription") String description,
            @RequestParam(value = "category",  required = false) String category,
            @RequestParam(value = "itemPhoto", required = false) MultipartFile photo,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            branchService.editItem(user, branchId, itemId, name, description, price,
                    category != null ? category : "");
            return ResponseEntity.ok(Map.of("success", true, "message", "Item updated"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/branch/{branchId}/items/{itemId}
     * Delete an item.
     */
    @DeleteMapping("/api/branch/{branchId}/items/{itemId}")
    public ResponseEntity<?> deleteItem(
            @PathVariable Long branchId,
            @PathVariable Long itemId,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            branchService.deleteItem(user, branchId, itemId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Item deleted"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}