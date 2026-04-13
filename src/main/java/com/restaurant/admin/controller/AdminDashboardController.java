package com.restaurant.admin.controller;

import com.restaurant.admin.dto.BranchDTO;
import com.restaurant.admin.dto.RestaurantWithBranchesDTO;
import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.BranchService;
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

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private SimpleUserService userService;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        return e.isEmpty() ? null : e.toLowerCase();
    }

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
            user = userService.findByEmail(principal.getName());
        }
        return user;
    }

    // ── Helper: build BranchDTO without address/phone ─────────────────────────

    /**
     * Constructs a BranchDTO from the simplified Branch model (no address/phone).
     * Adjust BranchDTO constructor to match whichever fields it actually declares.
     */
    private BranchDTO toBranchDTO(Branch branch) {
        BranchDTO dto = new BranchDTO();
        dto.setId(branch.getId());
        dto.setRestaurantId(branch.getRestaurant() != null ? branch.getRestaurant().getId() : null);
        dto.setBranchName(branch.getBranchName());
        dto.setActive(branch.isActive());
        dto.setCreatedAt(branch.getCreatedAt());
        dto.setUpdatedAt(branch.getUpdatedAt());
        return dto;
    }

    // ── Page endpoints ────────────────────────────────────────────────────────

    @GetMapping("/restaurants")
    public String adminDashboard(Principal principal,
                                 Model model,
                                 @RequestParam(required = false) Long restaurantId) {
        if (principal == null) return "redirect:/login";

        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) return "redirect:/login";

            Restaurant restaurant;
            if (restaurantId != null) {
                restaurant = restaurantService.getRestaurantById(restaurantId)
                        .orElseThrow(() -> new RuntimeException("Restaurant not found"));
                if (!restaurant.getUser().getId().equals(user.getId())) {
                    return "redirect:/restaurants";
                }
            } else {
                restaurant = restaurantService.getRestaurantByUser(user)
                        .orElseThrow(() -> new RuntimeException("No restaurant found"));
            }

                List<Branch> branches = branchService.getAllBranchesForUser(user).stream()
                    .filter(b -> {
                    Long bid = b.getRestaurant() != null ? b.getRestaurant().getId() : null;
                    return bid != null && bid.equals(restaurant.getId());
                    })
                    .toList();

            model.addAttribute("restaurant", restaurant);
            model.addAttribute("branches", branches);
            model.addAttribute("isMultiBranch", branches.size() > 1);
            model.addAttribute("branchCount", branches.size());
            model.addAttribute("restaurantId", restaurant.getId());

            return "admin-dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load dashboard");
            return "error";
        }
    }

    // ── JSON API endpoints ────────────────────────────────────────────────────

    @GetMapping("/api/restaurants/dashboard")
    @ResponseBody
    public ResponseEntity<?> getDashboardData(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Not authenticated"));
        }
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found"));

            List<Branch> branches = branchService.getAllBranchesForUser(user);

            List<BranchDTO> branchDTOs = new ArrayList<>();
            for (Branch branch : branches) {
                branchDTOs.add(toBranchDTO(branch));
            }

            RestaurantWithBranchesDTO dto = new RestaurantWithBranchesDTO(
                    restaurant.getId(),
                    restaurant.getRestaurantName(),
                    restaurant.getRestaurantType(),
                    restaurantService.toPublicLogoUrl(restaurant.getLogoPath()),
                    restaurant.getMenuBackgroundColor(),
                    branches.size() > 1,
                    branchDTOs
            );

            return ResponseEntity.ok(Map.of("success", true, "data", dto));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to load dashboard data"));
        }
    }

    @PostMapping("/api/branches")
    @ResponseBody
    public ResponseEntity<?> createBranch(@RequestBody Map<String, String> payload,
                                          Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Not authenticated"));
        }
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            String branchName = payload.get("branchName");
            if (branchName == null || branchName.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "branchName is required"));
            }

            // Resolve the restaurant to create the branch under
            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("No restaurant found"));

            Branch branch = branchService.createBranch(user, restaurant.getId(), branchName.trim());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "message", "Branch created",
                            "data", toBranchDTO(branch)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to create branch"));
        }
    }

    @GetMapping("/api/branches/{branchId}")
    @ResponseBody
    public ResponseEntity<?> getBranch(@PathVariable Long branchId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Not authenticated"));
        }
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            Branch branch = branchService.getBranchForUser(user, branchId);
            return ResponseEntity.ok(Map.of("success", true, "data", toBranchDTO(branch)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to get branch"));
        }
    }

    @PutMapping("/api/branches/{branchId}")
    @ResponseBody
    public ResponseEntity<?> updateBranch(@PathVariable Long branchId,
                                          @RequestBody Map<String, String> payload,
                                          Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Not authenticated"));
        }
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            String branchName = payload.get("branchName");
            if (branchName == null || branchName.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "branchName is required"));
            }

            // address + phone forwarded for signature compatibility; ignored internally
            Branch branch = branchService.updateBranch(user, branchId,
                    branchName,
                    payload.getOrDefault("address", ""),
                    payload.getOrDefault("phone", ""));

            return ResponseEntity.ok(Map.of("success", true, "message", "Branch updated",
                    "data", toBranchDTO(branch)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to update branch"));
        }
    }

    @DeleteMapping("/api/branches/{branchId}")
    @ResponseBody
    public ResponseEntity<?> deleteBranch(@PathVariable Long branchId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Not authenticated"));
        }
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            branchService.deleteBranch(user, branchId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Branch deleted"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to delete branch"));
        }
    }
}