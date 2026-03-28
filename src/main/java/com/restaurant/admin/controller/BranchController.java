package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
public class BranchController {

    @Autowired
    private BranchService branchService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SimpleUserService userService;

    // ── Auth helpers (same pattern as PageController) ─────────────────────────

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

    // ── Branch List ───────────────────────────────────────────────────────────

    @GetMapping("/branches")
    public String branchList(@RequestParam("restaurantId") Long restaurantId,
                             Principal principal,
                             Model model) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        Restaurant restaurant = restaurantService.getRestaurantById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        if (!restaurant.getUser().getId().equals(user.getId())) {
            return "redirect:/restaurants";
        }

        List<Branch> branches = branchService.getBranchesForRestaurant(user, restaurantId);

        model.addAttribute("restaurant", restaurant);
        model.addAttribute("branches", branches);
        model.addAttribute("hasBranches", !branches.isEmpty());
        model.addAttribute("restaurantId", restaurantId);
        return "branch-list";
    }

    // ── Create Branch ─────────────────────────────────────────────────────────

    @PostMapping("/branch/create")
    public String createBranch(@RequestParam("restaurantId") Long restaurantId,
                               @RequestParam("branchName") String branchName,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.createBranch(user, restaurantId, branchName.trim());
            redirectAttributes.addFlashAttribute("success", "Branch created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create branch: " + e.getMessage());
        }
        return "redirect:/branches?restaurantId=" + restaurantId;
    }

    // ── Branch Dashboard ──────────────────────────────────────────────────────

    @GetMapping("/branch/{branchId}/dashboard")
    public String branchDashboard(@PathVariable Long branchId,
                                  Principal principal,
                                  Model model) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        Branch branch = branchService.getBranchForUser(user, branchId);
        model.addAttribute("branch", branch);
        model.addAttribute("restaurant", branch.getRestaurant());
        return "branch-dashboard";
    }

    // ── Delete Branch ─────────────────────────────────────────────────────────

    @PostMapping("/branch/{branchId}/delete")
    public String deleteBranch(@PathVariable Long branchId,
                               @RequestParam("restaurantId") Long restaurantId,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.deleteBranch(user, branchId);
            redirectAttributes.addFlashAttribute("success", "Branch deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not delete branch: " + e.getMessage());
        }
        return "redirect:/branches?restaurantId=" + restaurantId;
    }

    // ── Rename Branch ─────────────────────────────────────────────────────────

    @PostMapping("/branch/{branchId}/rename")
    public String renameBranch(@PathVariable Long branchId,
                               @RequestParam("restaurantId") Long restaurantId,
                               @RequestParam("branchName") String branchName,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";

        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.renameBranch(user, branchId, branchName.trim());
            redirectAttributes.addFlashAttribute("success", "Branch renamed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not rename branch: " + e.getMessage());
        }
        return "redirect:/branches?restaurantId=" + restaurantId;
    }
}