package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.BranchService.EffectiveMenuItem;
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
@RequestMapping("/branch/{branchId}/menu")
public class BranchMenuController {

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
        if (principal instanceof OidcUser oidcUser) return normalizeEmail(oidcUser.getEmail());
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

    // ── View menu ─────────────────────────────────────────────────────────────

    @GetMapping
    public String viewBranchMenu(@PathVariable Long branchId,
                                 Principal principal,
                                 Model model) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        Branch branch = branchService.getBranchForUser(user, branchId);
        List<EffectiveMenuItem> effectiveMenu = branchService.buildEffectiveMenu(branch);

        model.addAttribute("branch", branch);
        model.addAttribute("restaurant", branch.getRestaurant());
        model.addAttribute("effectiveMenu", effectiveMenu);
        model.addAttribute("hasItems", !effectiveMenu.isEmpty());
        return "branch-menu-manage";
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    @GetMapping("/add")
    public String showAddItemForm(@PathVariable Long branchId,
                                  Principal principal,
                                  Model model) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        Branch branch = branchService.getBranchForUser(user, branchId);
        model.addAttribute("branch", branch);
        model.addAttribute("restaurant", branch.getRestaurant());
        return "branch-item-add";
    }

    @PostMapping("/add")
    public String addItem(@PathVariable Long branchId,
                          @RequestParam("name") String name,
                          @RequestParam("description") String description,
                          @RequestParam("price") Double price,
                          @RequestParam("category") String category,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.addBranchOnlyItem(user, branchId, name, description, price, category);
            redirectAttributes.addFlashAttribute("success", "Item added!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add item: " + e.getMessage());
        }
        return "redirect:/branch/" + branchId + "/menu";
    }

    // ── Edit item ─────────────────────────────────────────────────────────────

    @GetMapping("/edit")
    public String showEditForm(@PathVariable Long branchId,
                               @RequestParam("itemId") Long itemId,
                               Principal principal,
                               Model model) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        Branch branch = branchService.getBranchForUser(user, branchId);
        List<EffectiveMenuItem> menu = branchService.buildEffectiveMenu(branch);

        // Find the item by branchItemId or restaurantItemId
        EffectiveMenuItem target = menu.stream()
                .filter(e -> itemId.equals(e.getBranchItemId()) || itemId.equals(e.getRestaurantItemId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not found"));

        model.addAttribute("branch", branch);
        model.addAttribute("restaurant", branch.getRestaurant());
        model.addAttribute("item", target);
        model.addAttribute("itemId", itemId);
        return "branch-item-edit";
    }

    @PostMapping("/edit")
    public String saveEdit(@PathVariable Long branchId,
                           @RequestParam("itemId") Long itemId,
                           @RequestParam("name") String name,
                           @RequestParam("description") String description,
                           @RequestParam("price") Double price,
                           @RequestParam("category") String category,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.editItem(user, branchId, itemId, name, description, price, category);
            redirectAttributes.addFlashAttribute("success", "Item updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update: " + e.getMessage());
        }
        return "redirect:/branch/" + branchId + "/menu";
    }

    // ── Delete item ───────────────────────────────────────────────────────────

    @PostMapping("/delete")
    public String deleteItem(@PathVariable Long branchId,
                             @RequestParam("itemId") Long itemId,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.deleteItem(user, branchId, itemId);
            redirectAttributes.addFlashAttribute("success", "Item deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/branch/" + branchId + "/menu";
    }

    // ── Legacy endpoints (kept for template compatibility) ────────────────────

    @PostMapping("/hide")
    public String hideItem(@PathVariable Long branchId,
                           @RequestParam("parentItemId") Long parentItemId,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.hideInheritedItem(user, branchId, parentItemId);
            redirectAttributes.addFlashAttribute("success", "Item hidden.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/branch/" + branchId + "/menu";
    }

    @PostMapping("/restore")
    public String restoreItem(@PathVariable Long branchId,
                              @RequestParam("parentItemId") Long parentItemId,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        SimpleUser user = getCurrentUser(principal);
        if (user == null) return "redirect:/login";

        try {
            branchService.restoreInheritedItem(user, branchId, parentItemId);
            redirectAttributes.addFlashAttribute("success", "Item restored.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/branch/" + branchId + "/menu";
    }
}