package com.restaurant.admin.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    @Autowired private RestaurantService restaurantService;
    @Autowired private BranchService     branchService;
    @Autowired private SimpleUserService userService;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        return e.isEmpty() ? null : e.toLowerCase();
    }

    private String resolveEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser u)    return normalizeEmail(u.getEmail());
        if (principal instanceof OAuth2User u) {
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

    // ── Static pages ──────────────────────────────────────────────────────────

    @GetMapping("/")
    public String landing() { return "landing"; }

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String oauthError,
            @RequestParam(required = false) String logout,
            Model model) {
        if (error != null)      model.addAttribute("error",   "Invalid email or password");
        if (oauthError != null) model.addAttribute("error",   "Google login failed or was canceled. Please try again.");
        if (logout != null)     model.addAttribute("message", "You have been logged out successfully");
        return "login";
    }

    @GetMapping("/signup")
    public String signup() { return "signup"; }

    // NOTE: /dashboard is owned by BranchController

    @GetMapping("/enteritems")
    public String enterItems() { return "enteritems"; }

    @GetMapping("/manageitems")
    public String manageItems() { return "manageitems"; }

    // ── Branch-scoped pages — forward branchId to template ───────────────────

    /**
     * GET /menu/preview?branchId={id}
     * Authenticated preview — passes branchId so the template fetches the right branch.
     * Public preview comes from PublicMenuController (/menu/branch/{branchId}).
     */
    @GetMapping("/menu/preview")
    public String menuPreview(
            @RequestParam(required = false) Long branchId,
            Principal principal,
            Model model) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("publicMode",    false);
        model.addAttribute("branchId",      branchId);
        model.addAttribute("restaurantId",  null);
        return "menu-preview";
    }

    /**
     * GET /menu/theme?branchId={id}
     * The template reads branchId from the URL itself (JS), so we just return the view.
     */
    @GetMapping("/menu/theme")
    public String menuTheme(Principal principal) {
        if (principal == null) return "redirect:/login";
        return "menu-theme";
    }

    /**
     * GET /qr-page?branchId={id}
     * The template reads branchId from the URL itself (JS), so we just return the view.
     */
    @GetMapping("/qr-page")
    public String qrPage(Principal principal) {
        if (principal == null) return "redirect:/login";
        return "qr-page";
    }

    // ── Restaurant list ───────────────────────────────────────────────────────

    @GetMapping("/restaurants")
    public String restaurantList(Principal principal, Model model) {
        try {
            if (principal == null) return "redirect:/login";

            SimpleUser user = getCurrentUser(principal);
            if (user == null) return "redirect:/login";

            List<Restaurant> restaurants = restaurantService.getRestaurantsByUser(user);
            if (restaurants == null) restaurants = new ArrayList<>();

            Map<Long, String> logoUrls = new HashMap<>();
            for (Restaurant restaurant : restaurants) {
                String publicUrl = restaurantService.toPublicLogoUrl(restaurant.getLogoPath());
                if (publicUrl != null) logoUrls.put(restaurant.getId(), publicUrl);
            }

            model.addAttribute("restaurants",    restaurants);
            model.addAttribute("hasRestaurants", !restaurants.isEmpty());
            model.addAttribute("logoUrls",       logoUrls);

            return "restaurant-list";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load restaurant list: " + e.getMessage());
            model.addAttribute("restaurants",    new ArrayList<>());
            model.addAttribute("hasRestaurants", false);
            return "restaurant-list";
        }
    }
}