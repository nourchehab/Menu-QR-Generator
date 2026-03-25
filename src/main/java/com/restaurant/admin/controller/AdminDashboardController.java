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

/**
 * Admin Dashboard Controller
 * Handles multi-branch restaurant admin dashboard operations
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {
    
    @Autowired
    private RestaurantService restaurantService;
    
    @Autowired
    private BranchService branchService;
    
    @Autowired
    private SimpleUserService userService;
    
    /**
     * Normalize email helper
     */
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
     * Resolve email from Principal
     */
    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof Authentication auth) return resolveEmail(auth);
        return normalizeEmail(principal.getName());
    }
    
    /**
     * Get current SimpleUser from Principal
     */
    private SimpleUser getCurrentUser(Principal principal) {
        String email = resolveEmail(principal);
        if (email == null) return null;
        
        SimpleUser user = userService.findByEmail(email);
        if (user == null && principal != null) {
            user = userService.findByEmail(principal.getName());
        }
        return user;
    }
    
    /**
     * GET /admin/restaurants → View admin dashboard page
     * Optional param: restaurantId - to view specific restaurant dashboard
     */
    @GetMapping("/restaurants")
    public String adminDashboard(
            Principal principal, 
            Model model,
            @RequestParam(required = false) Long restaurantId) {
        if (principal == null) {
            return "redirect:/login";
        }
        
        try {
            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return "redirect:/login";
            }
            
            Restaurant restaurant;
            
            if (restaurantId != null) {
                // Get specific restaurant and verify ownership
                restaurant = restaurantService.getRestaurantById(restaurantId)
                        .orElseThrow(() -> new RuntimeException("Restaurant not found"));
                
                // Verify user owns this restaurant
                if (!restaurant.getUser().getId().equals(user.getId())) {
                    return "redirect:/restaurants";
                }
            } else {
                // Get the user's single restaurant (OneToOne relationship)
                restaurant = restaurantService.getRestaurantByUser(user)
                        .orElseThrow(() -> new RuntimeException("No restaurant found"));
            }
            
            // Get branches for this specific restaurant
            List<Branch> branches = branchService.getAllBranchesForUser(user);
            // Filter branches for this restaurant
            branches = branches.stream()
                    .filter(b -> b.getRestaurant().getId().equals(restaurant.getId()))
                    .toList();
            
            boolean isMultiBranch = branches.size() > 1;
            
            model.addAttribute("restaurant", restaurant);
            model.addAttribute("branches", branches);
            model.addAttribute("isMultiBranch", isMultiBranch);
            model.addAttribute("branchCount", branches.size());
            model.addAttribute("restaurantId", restaurant.getId());
            
            return "admin-dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load dashboard");
            return "error";
        }
    }
    
    /**
     * GET /api/admin/restaurants/dashboard → JSON API for restaurants + branches
     * Returns: { success: true, data: RestaurantWithBranchesDTO }
     */
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
            
            // Get restaurant
            Restaurant restaurant = restaurantService.getRestaurantByUser(user)
                    .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));
            
            // Get branches
            List<Branch> branches = branchService.getAllBranchesForUser(user);
            
            // Convert branches to DTOs
            List<BranchDTO> branchDTOs = new ArrayList<>();
            for (Branch branch : branches) {
                BranchDTO dto = new BranchDTO(
                    branch.getId(),
                    branch.getBranchName(),
                    branch.getAddress(),
                    branch.getPhone(),
                    branch.isActive(),
                    branch.getCreatedAt()
                );
                dto.setUpdatedAt(branch.getUpdatedAt());
                branchDTOs.add(dto);
            }
            
            // Compute isMultiBranch flag
            boolean isMultiBranch = branches.size() > 1;
            
            // Build response DTO
            RestaurantWithBranchesDTO dto = new RestaurantWithBranchesDTO(
                restaurant.getId(),
                restaurant.getRestaurantName(),
                restaurant.getRestaurantType(),
                restaurantService.toPublicLogoUrl(restaurant.getLogoPath()),
                restaurant.getMenuBackgroundColor(),
                isMultiBranch,
                branchDTOs
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dto);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to load dashboard data"));
        }
    }
    
    /**
     * POST /api/admin/branches → Create a new branch
     * Body: { branchName, address, phone }
     */
    @PostMapping("/api/branches")
    @ResponseBody
    public ResponseEntity<?> createBranch(
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
            String address = payload.get("address");
            String phone = payload.get("phone");
            
            if (branchName == null || branchName.isBlank() ||
                address == null || address.isBlank() ||
                phone == null || phone.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Missing required fields"));
            }
            
            Branch branch = branchService.createBranch(user, branchName, address, phone);
            
            BranchDTO dto = new BranchDTO(
                branch.getId(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.isActive(),
                branch.getCreatedAt()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "message", "Branch created", "data", dto));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to create branch"));
        }
    }
    
    /**
     * GET /api/admin/branches/:branchId → Get a specific branch
     */
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
            
            Branch branch = branchService.getBranchForUser(user, branchId)
                    .orElseThrow(() -> new SecurityException("Branch not found or not owned by user"));
            
            BranchDTO dto = new BranchDTO(
                branch.getId(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.isActive(),
                branch.getCreatedAt()
            );
            dto.setUpdatedAt(branch.getUpdatedAt());
            
            return ResponseEntity.ok(Map.of("success", true, "data", dto));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to get branch"));
        }
    }
    
    /**
     * PUT /api/admin/branches/:branchId → Update a branch
     * Body: { branchName, address, phone }
     */
    @PutMapping("/api/branches/{branchId}")
    @ResponseBody
    public ResponseEntity<?> updateBranch(
            @PathVariable Long branchId,
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
            String address = payload.get("address");
            String phone = payload.get("phone");
            
            if (branchName == null || branchName.isBlank() ||
                address == null || address.isBlank() ||
                phone == null || phone.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Missing required fields"));
            }
            
            Branch branch = branchService.updateBranch(user, branchId, branchName, address, phone);
            
            BranchDTO dto = new BranchDTO(
                branch.getId(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.isActive(),
                branch.getCreatedAt()
            );
            dto.setUpdatedAt(branch.getUpdatedAt());
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Branch updated", "data", dto));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to update branch"));
        }
    }
    
    /**
     * DELETE /api/admin/branches/:branchId → Delete a branch
     */
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
