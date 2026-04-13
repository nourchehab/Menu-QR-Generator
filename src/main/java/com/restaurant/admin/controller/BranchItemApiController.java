package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.BranchService.EffectiveMenuItem;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class BranchItemApiController {

    @Autowired private BranchService     branchService;
    @Autowired private SimpleUserService userService;
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

    // ── PUBLIC ────────────────────────────────────────────────────────────────

    @GetMapping("/api/public/branch/{branchId}/items")
    public ResponseEntity<?> getPublicBranchItems(@PathVariable Long branchId) {
        try {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            return ResponseEntity.ok(branchService.buildEffectiveMenu(branch));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET sibling branches (for copy/delete checklists) ────────────────────

    @GetMapping("/api/branch/{branchId}/sibling-branches")
    public ResponseEntity<?> getSiblingBranches(@PathVariable Long branchId, Principal principal) {
        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            Branch branch = branchService.getBranchForUser(user, branchId);
            Long restaurantId = branch.getRestaurant() != null ? branch.getRestaurant().getId() : null;
            if (restaurantId == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Restaurant not found"));

            List<Branch> siblings = branchService.getNonMainBranchesForRestaurant(user, restaurantId);
            List<Map<String, Object>> result = siblings.stream().map(b -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id",         b.getId());
                m.put("branchName", b.getBranchName());
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
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

    /**
     * POST /api/branch/{branchId}/items
     * ✅ ALL branches (including main) store in BranchMenuItem — no MenuItem table.
     * Returns branchItemId so the frontend can offer copy-to-branches.
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

            var newItem = branchService.addItem(
                    user, branchId, name, description, price,
                    category != null ? category : "", photo);

            Map<String, Object> response = new HashMap<>();
            response.put("success",      true);
            response.put("message",      "Item added");
            response.put("isMainBranch", branch.isMainBranch());
            // ✅ Return branchItemId (not menuItemId) for copy-to-branches
            response.put("branchItemId", newItem.getId());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── COPY item to selected branches ────────────────────────────────────────

    /**
     * POST /api/branch/{branchId}/items/{branchItemId}/copy-to-branches
     * Body: { "branchIds": [2, 3] }
     * Copies a BranchMenuItem to other branches by reference (no S3 re-upload).
     */
    @PostMapping("/api/branch/{branchId}/items/{branchItemId}/copy-to-branches")
    public ResponseEntity<?> copyItemToBranches(
            @PathVariable Long branchId,
            @PathVariable Long branchItemId,
            @RequestBody Map<String, List<Long>> body,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            List<Long> targetBranchIds = body.get("branchIds");
            if (targetBranchIds == null || targetBranchIds.isEmpty())
                return ResponseEntity.ok(Map.of("success", true, "message", "No branches selected"));

            branchService.copyItemToBranches(user, branchItemId, targetBranchIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "Item copied to selected branches"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE item from selected branches ────────────────────────────────────

    /**
     * POST /api/branch/{branchId}/items/delete-from-branches
     * Body: { "itemName": "Burger", "branchIds": [2, 3] }
     * ✅ Does NOT delete S3 photos.
     */
    @PostMapping("/api/branch/{branchId}/items/delete-from-branches")
    public ResponseEntity<?> deleteItemFromBranches(
            @PathVariable Long branchId,
            @RequestBody Map<String, Object> body,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));

            String itemName = (String) body.get("itemName");
            @SuppressWarnings("unchecked")
            List<Integer> rawIds = (List<Integer>) body.get("branchIds");
            if (itemName == null || rawIds == null || rawIds.isEmpty())
                return ResponseEntity.ok(Map.of("success", true, "message", "No branches selected"));

            List<Long> targetBranchIds = rawIds.stream().map(Long::valueOf).toList();
            branchService.deleteItemFromBranches(user, itemName, targetBranchIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "Item deleted from selected branches"));
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

            // ✅ All branches use BranchMenuItem edit
            branchService.editItem(user, branchId, itemId, name, description, price,
                    category != null ? category : "", photo);

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

            Branch branch = branchService.getBranchForUser(user, branchId);
            boolean isMain = branch.isMainBranch();

            // Get item name before deleting (for delete-from-branches dialog)
            String deletedItemName = null;
            try {
                var items = branchService.getEffectiveMenu(user, branchId);
                deletedItemName = items.stream()
                        .filter(i -> itemId.equals(i.getBranchItemId()))
                        .map(EffectiveMenuItem::getName)
                        .findFirst().orElse(null);
            } catch (Exception ignored) {}

            branchService.deleteItem(user, branchId, itemId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",      true);
            response.put("message",      "Item deleted");
            response.put("isMainBranch", isMain);
            if (deletedItemName != null) response.put("deletedItemName", deletedItemName);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── DELETE image only ─────────────────────────────────────────────────────

    /**
     * DELETE /api/branch/{branchId}/items/{itemId}/image
     * ✅ Only deletes from S3 if no other branch references the same photo path.
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

            branchService.deleteItemImage(user, branchId, itemId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Image removed"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}