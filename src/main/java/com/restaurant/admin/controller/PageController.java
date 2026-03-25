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


    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String oauthError,
            @RequestParam(required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }

        if (oauthError != null) {
            model.addAttribute("error", "Google login failed or was canceled. Please try again.");
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }

        return "login";
    }

    // Spring Security handles POST /login

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long restaurantId, Model model, Principal principal) {
        // If restaurantId is provided, load that specific restaurant
        if (restaurantId != null) {
            try {
                restaurantService.getRestaurantById(restaurantId).ifPresent(restaurant -> {
                    model.addAttribute("restaurant", restaurant);
                    model.addAttribute("restaurantId", restaurantId);
                });
            } catch (Exception e) {
                // If restaurant not found or error, fall through to normal dashboard
            }
        }
        return "dashboard";
    }

    @GetMapping("/enteritems")
    public String enterItems() {
        return "enteritems";
    }

    @GetMapping("/manageitems")
    public String manageItems() {
        return "manageitems";
    }

    @GetMapping("/menu/preview")
    public String menuPreview(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("publicMode", false);
        return "menu-preview";
    }
@GetMapping("/menu/theme")
    public String menuTheme(Principal principal) {
        if (principal == null) return "redirect:/login";
        return "menu-theme";
    }
    @GetMapping("/qr-page")
    public String qrPage() {
        return "qr-page";
    }

    @GetMapping("/restaurants")
    public String restaurantList(Principal principal, Model model) {
        try {
            if (principal == null) {
                return "redirect:/login";
            }

            SimpleUser user = getCurrentUser(principal);
            if (user == null) {
                return "redirect:/login";
            }

            // Get all restaurants for user
            List<Restaurant> restaurants = restaurantService.getRestaurantsByUser(user);
            if (restaurants == null) {
                restaurants = new ArrayList<>();
            }
            
            // Process logo URLs for each restaurant
            Map<Long, String> logoUrls = new HashMap<>();
            for (Restaurant restaurant : restaurants) {
                String publicUrl = restaurantService.toPublicLogoUrl(restaurant.getLogoPath());
                if (publicUrl != null) {
                    logoUrls.put(restaurant.getId(), publicUrl);
                }
            }
            
            model.addAttribute("restaurants", restaurants);
            model.addAttribute("hasRestaurants", !restaurants.isEmpty());
            model.addAttribute("logoUrls", logoUrls);

            return "restaurant-list";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load restaurant list: " + e.getMessage());
            model.addAttribute("restaurants", new ArrayList<>());
            model.addAttribute("hasRestaurants", false);
            return "restaurant-list";
        }
    }
}
