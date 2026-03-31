package com.restaurant.admin.controller;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class PublicMenuController {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private BranchRepository branchRepository;

    /**
     * GET /r/{restaurantId}
     * ✅ Smart QR landing — what every QR code points to.
     *
     * Logic:
     *  - 0 branches → show empty menu page
     *  - 1 branch   → redirect straight to /b/{branchId} (menu)
     *  - 2+ branches → show branch picker page
     */
    @GetMapping("/r/{restaurantId}")
    public String restaurantLanding(@PathVariable Long restaurantId, Model model) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            model.addAttribute("error", "Restaurant not found");
            return "error";
        }

        List<Branch> branches = branchRepository
                .findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(restaurant);

        if (branches.size() == 1) {
            // Single branch — go straight to its menu
            return "redirect:/b/" + branches.get(0).getId();
        }

        if (branches.isEmpty()) {
            // No branches yet — show empty state on menu preview
            model.addAttribute("publicMode",   true);
            model.addAttribute("restaurantId", restaurantId);
            model.addAttribute("branchId",     null);
            return "menu-preview";
        }

        // Multiple branches — show picker
        model.addAttribute("restaurant", restaurant);
        model.addAttribute("branches",   branches);
        model.addAttribute("logoUrl",    restaurant.getLogoPath()); // raw path, JS can handle
        return "branch-picker";
    }

    /**
     * GET /b/{branchId}
     * ✅ Branch public menu — conflict-free short URL.
     * No sibling /b/something routes with String path vars to avoid Long parse errors.
     */
    @GetMapping("/b/{branchId}")
    public String viewBranchPublicMenu(@PathVariable Long branchId, Model model) {
        model.addAttribute("publicMode",   true);
        model.addAttribute("branchId",     branchId);
        model.addAttribute("restaurantId", null);
        return "menu-preview";
    }

    /**
     * GET /menu/{restaurantId}
     * Legacy public menu — kept so old QR codes still work.
     * Now redirects through /r/ for consistent behaviour.
     */
    @GetMapping("/menu/{restaurantId}")
    public String viewPublicMenu(@PathVariable Long restaurantId, Model model) {
        return "redirect:/r/" + restaurantId;
    }

    /** /m/{id} alias — legacy QR codes. */
    @GetMapping("/m/{restaurantId}")
    public String publicMenuAlias(@PathVariable Long restaurantId, Model model) {
        return "redirect:/r/" + restaurantId;
    }
}