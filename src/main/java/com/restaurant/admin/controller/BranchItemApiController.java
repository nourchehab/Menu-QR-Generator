package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.BranchMenuItemRepository;
import com.restaurant.admin.model.BranchMenuItem;
import com.restaurant.admin.service.AiCategoryService;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.BranchService.EffectiveMenuItem;
import com.restaurant.admin.service.MenuItemService;
import com.restaurant.admin.service.MenuItemImageStorageService;
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

    @Autowired private BranchService              branchService;
    @Autowired private SimpleUserService          userService;
    @Autowired private MenuItemService            menuItemService;
    @Autowired private MenuItemImageStorageService imageStorageService;
    @Autowired private BranchRepository           branchRepository;
    @Autowired private BranchMenuItemRepository   branchMenuItemRepository;
    @Autowired private AiCategoryService          aiCategoryService;

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

    // ── PUBLIC ────────────────────────────────────────────────────────────────

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

    // ── GET branch details ────────────────────────────────────────────────────

    @GetMapping("/api/branches/{branchId}")
    public ResponseEntity<?> getBranchDetails(@PathVariable Long branchId, Principal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Branch branch = branchService.getBranchForUser(user, branchId);
            
            return ResponseEntity.ok(Map.of(
                    "branchId", branch.getId(),
                    "branchName", branch.getBranchName(),
                    "restaurantId", branch.getRestaurant().getId()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET items ─────────────────────────────────────────────────────────────

    @GetMapping("/api/branch/{branchId}/items")
    public ResponseEntity<?> getItems(@PathVariable Long branchId, Principal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            return ResponseEntity.ok(branchService.getEffectiveMenu(user, branchId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── ADD item ──────────────────────────────────────────────────────────────

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
                // ✅ Main branch → real MenuItem table (S3 photo via MenuItemService)
                BigDecimal bdPrice = price != null ? BigDecimal.valueOf(price) : BigDecimal.ZERO;
                menuItemService.addMenuItem(branch.getRestaurant().getId(), name, bdPrice,
                        description, photo, category != null ? category : "");
            } else {
                // ✅ Snapshot branch → BranchMenuItem (S3 photo via BranchService)
                branchService.addBranchOnlyItem(user, branchId, name, description, price,
                        category != null ? category : "", photo);
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Item added"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── EDIT item ─────────────────────────────────────────────────────────────

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

            Branch branch = branchService.getBranchForUser(user, branchId);

            if (branch.isMainBranch()) {
                // ✅ Main branch: update real MenuItem with S3 photo replacement
                BigDecimal bdPrice = price != null ? BigDecimal.valueOf(price) : BigDecimal.ZERO;
                menuItemService.updateMenuItem(itemId, name, bdPrice, description, photo,
                        category != null ? category : "");
            } else {
                // ✅ Snapshot branch: update BranchMenuItem with S3 photo replacement
                branchService.editBranchItem(user, branchId, itemId, name, description, price,
                        category != null ? category : "", photo);
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Item updated"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE item ───────────────────────────────────────────────────────────

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

    // ── DELETE image only ─────────────────────────────────────────────────────

    /**
     * DELETE /api/branch/{branchId}/items/{itemId}/image
     * Removes only the photo from a branch item (main branch or snapshot).
     */
    @DeleteMapping("/api/branch/{branchId}/items/{itemId}/image")
    public ResponseEntity<?> deleteItemImage(
            @PathVariable Long branchId,
            @PathVariable Long itemId,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Branch branch = branchService.getBranchForUser(user, branchId);

            if (branch.isMainBranch()) {
                // Delegate to existing MenuItemService image delete
                menuItemService.deleteMenuItemImage(itemId);
            } else {
                // Find BranchMenuItem and clear its photo
                BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                        .orElseThrow(() -> new RuntimeException("Item not found"));
                imageStorageService.deleteIfExists(bmi.getPhotoPath());
                bmi.setPhotoPath(null);
                branchMenuItemRepository.save(bmi);
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Image deleted"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/restaurants/{restaurantId}/batch-categorize")
    public ResponseEntity<?> batchCategorizeBranchItems(
            @PathVariable Long restaurantId,
            @RequestParam Long branchId,
            @RequestBody Map<String, Object> body,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Branch branch = branchService.getBranchForUser(user, branchId);
            
            // Verify branch belongs to the specified restaurant
            if (!branch.getRestaurant().getId().equals(restaurantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Branch does not belong to restaurant"));
            }

            // Extract itemIds from request body
            @SuppressWarnings("unchecked")
            List<Long> itemIds = (List<Long>) body.get("itemIds");
            if (itemIds == null || itemIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "itemIds not provided"));
            }

            // Fetch BranchMenuItems for the given IDs
            List<BranchMenuItem> items = new java.util.ArrayList<>();
            for (Long itemId : itemIds) {
                branchMenuItemRepository.findByIdAndBranch(itemId, branch).ifPresent(items::add);
            }

            if (items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No valid items found"));
            }

            // Call AI categorization service for batch processing
            List<Map<String, Object>> results = aiCategoryService.categorizeBranchMenuItemsBatch(items, restaurantId, branchId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Items categorized successfully",
                    "results", results
            ));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}