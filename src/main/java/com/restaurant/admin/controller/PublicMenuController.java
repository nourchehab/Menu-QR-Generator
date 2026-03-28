package com.restaurant.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicMenuController {

    /**
     * GET /menu/{restaurantId}
     * Legacy public menu — kept so old QR codes still work.
     * IMPORTANT: Do NOT add /menu/branch/{x} or any /menu/{word}/{x} sibling route
     * because Spring will try to cast the word as Long and throw NumberFormatException.
     */
    @GetMapping("/menu/{restaurantId}")
    public String viewPublicMenu(@PathVariable Long restaurantId, Model model) {
        model.addAttribute("publicMode",   true);
        model.addAttribute("restaurantId", restaurantId);
        model.addAttribute("branchId",     null);
        return "menu-preview";
    }

    /** /m/{id} alias so old QR codes still work. */
    @GetMapping("/m/{restaurantId}")
    public String publicMenuAlias(@PathVariable Long restaurantId, Model model) {
        model.addAttribute("publicMode",   true);
        model.addAttribute("restaurantId", restaurantId);
        model.addAttribute("branchId",     null);
        return "menu-preview";
    }

    /**
     * GET /b/{branchId}
     * ✅ Branch-scoped public menu. Uses /b/ prefix to avoid the
     * /menu/{restaurantId} path conflict (Spring can't distinguish
     * /menu/branch/{id} from /menu/{restaurantId} and tries to parse
     * "branch" as a Long, which throws NumberFormatException).
     * QrController generates QR codes pointing to this URL.
     */
    @GetMapping("/b/{branchId}")
    public String viewBranchPublicMenu(@PathVariable Long branchId, Model model) {
        model.addAttribute("publicMode",   true);
        model.addAttribute("branchId",     branchId);
        model.addAttribute("restaurantId", null);
        return "menu-preview";
    }
}