package com.restaurant.admin.service;

import com.restaurant.admin.model.*;
import com.restaurant.admin.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BranchService {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private BranchMenuItemRepository branchMenuItemRepository;

    // ── Main branch auto-creation ─────────────────────────────────────────────

    /**
     * Ensures a main branch exists for the restaurant.
     * Called from RestaurantService after restaurant setup.
     * Safe to call multiple times — only creates if none exists.
     */
    @Transactional
    public Branch ensureMainBranch(Restaurant restaurant) {
        return branchRepository.findFirstByRestaurantAndIsMainBranchTrue(restaurant)
                .orElseGet(() -> {
                    Branch main = new Branch("Main Branch", restaurant, true);
                    return branchRepository.save(main);
                });
    }

    // ── Branch CRUD ───────────────────────────────────────────────────────────

    /**
     * Create a new branch and snapshot the current restaurant menu into it.
     */
    @Transactional
    public Branch createBranch(SimpleUser user, Long restaurantId, String branchName) {
        Restaurant restaurant = getOwnedRestaurant(user, restaurantId);
        Branch branch = new Branch(branchName, restaurant, false);
        branch = branchRepository.save(branch);

        // Snapshot current restaurant menu items as independent BranchMenuItems
        List<MenuItem> currentItems = menuItemRepository.findByRestaurant(restaurant);
        for (MenuItem item : currentItems) {
            BranchMenuItem snapshot = new BranchMenuItem();
            snapshot.setBranch(branch);
            snapshot.setParentItem(null);        // no parent — fully independent copy
            snapshot.setHidden(false);
            snapshot.setName(item.getItemName());
            snapshot.setDescription(item.getItemDescription());
            snapshot.setPrice(item.getItemPrice() != null
                    ? item.getItemPrice().doubleValue() : null);
            snapshot.setCategory(item.getCategory());
            branchMenuItemRepository.save(snapshot);
        }

        return branch;
    }

    /**
     * Get all branches for a restaurant, main branch always first.
     */
    public List<Branch> getBranchesForRestaurant(SimpleUser user, Long restaurantId) {
        Restaurant restaurant = getOwnedRestaurant(user, restaurantId);
        // Ensure main branch exists (handles existing restaurants created before this feature)
        ensureMainBranch(restaurant);
        List<Branch> branches = branchRepository.findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(restaurant);
        return branches;
    }

    public List<Branch> getAllBranchesForUser(SimpleUser user) {
        List<Restaurant> restaurants = restaurantRepository.findAllByUser(user);
        List<Branch> all = new ArrayList<>();
        for (Restaurant r : restaurants) {
            ensureMainBranch(r);
            all.addAll(branchRepository.findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(r));
        }
        return all;
    }

    public List<Branch> getActiveBranchesForUser(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.findByRestaurantAndIsActiveTrue(restaurant);
    }

    public Branch getBranchForUser(SimpleUser user, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new SecurityException("Branch not found"));
        if (!branch.getRestaurant().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Branch not owned by user");
        }
        return branch;
    }

    public Optional<Branch> getBranchForUserOptional(SimpleUser user, Long branchId) {
        try {
            return Optional.of(getBranchForUser(user, branchId));
        } catch (SecurityException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Branch renameBranch(SimpleUser user, Long branchId, String newName) {
        Branch branch = getBranchForUser(user, branchId);
        branch.setBranchName(newName);
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch updateBranch(SimpleUser user, Long branchId,
                               String branchName,
                               @SuppressWarnings("unused") String address,
                               @SuppressWarnings("unused") String phone) {
        return renameBranch(user, branchId, branchName);
    }

    @Transactional
    public Branch toggleBranchStatus(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        if (branch.isMainBranch()) {
            throw new SecurityException("Cannot deactivate the main branch");
        }
        branch.setActive(!branch.isActive());
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }

    /**
     * Delete a branch. Main branch cannot be deleted.
     */
    @Transactional
    public void deleteBranch(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        if (branch.isMainBranch()) {
            throw new SecurityException("Cannot delete the main branch");
        }
        branchMenuItemRepository.deleteAllByBranch(branch);
        branchRepository.delete(branch);
    }

    public boolean isMultiBranch(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.countByRestaurant(restaurant) > 1;
    }

    // ── Branch Menu Resolution ────────────────────────────────────────────────

    /**
     * Build the effective menu for a branch.
     *
     * - Main branch: reads directly from restaurant MenuItem table (live).
     * - Other branches: reads from their own BranchMenuItem snapshot rows only.
     */
    public List<EffectiveMenuItem> getEffectiveMenu(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        return buildEffectiveMenu(branch);
    }

    public List<EffectiveMenuItem> buildEffectiveMenu(Branch branch) {
        if (branch.isMainBranch()) {
            return buildMainBranchMenu(branch);
        } else {
            return buildSnapshotMenu(branch);
        }
    }

    /** Main branch menu — reads live from restaurant MenuItem table. */
    private List<EffectiveMenuItem> buildMainBranchMenu(Branch branch) {
        List<MenuItem> items = menuItemRepository.findByRestaurant(branch.getRestaurant());
        List<EffectiveMenuItem> result = new ArrayList<>();
        for (MenuItem item : items) {
            EffectiveMenuItem emi = new EffectiveMenuItem();
            emi.setRestaurantItemId(item.getId());
            emi.setName(item.getItemName());
            emi.setDescription(item.getItemDescription());
            emi.setPrice(item.getItemPrice());
            emi.setCategory(item.getCategory());
            emi.setOverridden(false);
            emi.setBranchOnly(false);
            result.add(emi);
        }
        return result;
    }

    /** Non-main branch menu — reads from its own independent BranchMenuItem snapshot rows. */
    private List<EffectiveMenuItem> buildSnapshotMenu(Branch branch) {
        List<BranchMenuItem> rows = branchMenuItemRepository
                .findByBranchAndHiddenFalse(branch);
        List<EffectiveMenuItem> result = new ArrayList<>();
        for (BranchMenuItem bmi : rows) {
            EffectiveMenuItem emi = new EffectiveMenuItem();
            emi.setBranchItemId(bmi.getId());
            emi.setRestaurantItemId(null);       // fully independent
            emi.setName(bmi.getName());
            emi.setDescription(bmi.getDescription());
            emi.setPrice(bmi.getPrice() != null
                    ? BigDecimal.valueOf(bmi.getPrice()) : null);
            emi.setCategory(bmi.getCategory());
            emi.setOverridden(false);
            emi.setBranchOnly(true);
            result.add(emi);
        }
        return result;
    }

    // ── Branch Menu Item Operations ───────────────────────────────────────────

    @Transactional
    public BranchMenuItem addBranchOnlyItem(SimpleUser user, Long branchId,
                                            String name, String description,
                                            Double price, String category) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.setBranch(branch);
        bmi.setParentItem(null);
        bmi.setHidden(false);
        bmi.setName(name);
        bmi.setDescription(description);
        bmi.setPrice(price);
        bmi.setCategory(category);
        return branchMenuItemRepository.save(bmi);
    }

    /**
     * Edit a branch menu item (works for both main branch restaurant items and snapshot items).
     * For main branch: creates/updates a BranchMenuItem override linked to the parent.
     * For other branches: updates the snapshot BranchMenuItem row directly.
     */
    @Transactional
    public BranchMenuItem editItem(SimpleUser user, Long branchId,
                                   Long branchMenuItemId,
                                   String name, String description,
                                   Double price, String category) {
        Branch branch = getBranchForUser(user, branchId);

        if (branch.isMainBranch()) {
            // For main branch, branchMenuItemId is actually the restaurant MenuItem id
            // Update the parent MenuItem directly since main branch reads from MenuItem
            MenuItem parent = menuItemRepository.findById(branchMenuItemId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            parent.setItemName(name);
            parent.setItemDescription(description);
            parent.setItemPrice(java.math.BigDecimal.valueOf(price));
            parent.setCategory(category);
            menuItemRepository.save(parent);
            
            // Also maintain BranchMenuItem for consistency if needed
            BranchMenuItem bmi = branchMenuItemRepository
                    .findByBranchAndParentItem(branch, parent)
                    .orElse(new BranchMenuItem());
            bmi.setBranch(branch);
            bmi.setParentItem(parent);
            bmi.setHidden(false);
            bmi.setCategory(category);
            return branchMenuItemRepository.save(bmi);
        } else {
            // For snapshot branches, update the row directly
            BranchMenuItem bmi = branchMenuItemRepository
                    .findByIdAndBranch(branchMenuItemId, branch)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            bmi.setName(name);
            bmi.setDescription(description);
            bmi.setPrice(price);
            bmi.setCategory(category);
            return branchMenuItemRepository.save(bmi);
        }
    }

    @Transactional
    public void deleteItem(SimpleUser user, Long branchId, Long itemId) {
        Branch branch = getBranchForUser(user, branchId);

        if (branch.isMainBranch()) {
            // Hide the restaurant item from main branch view
            MenuItem parent = menuItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            BranchMenuItem bmi = branchMenuItemRepository
                    .findByBranchAndParentItem(branch, parent)
                    .orElse(new BranchMenuItem());
            bmi.setBranch(branch);
            bmi.setParentItem(parent);
            bmi.setHidden(true);
            branchMenuItemRepository.save(bmi);
        } else {
            // Delete the snapshot row outright
            BranchMenuItem bmi = branchMenuItemRepository
                    .findByIdAndBranch(itemId, branch)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            branchMenuItemRepository.delete(bmi);
        }
    }

    // Keep old methods for BranchMenuController compatibility
    @Transactional
    public BranchMenuItem overrideInheritedItem(SimpleUser user, Long branchId,
                                                Long parentItemId,
                                                String name, String description,
                                                Double price, String category) {
        return editItem(user, branchId, parentItemId, name, description, price, category);
    }

    @Transactional
    public void hideInheritedItem(SimpleUser user, Long branchId, Long parentItemId) {
        deleteItem(user, branchId, parentItemId);
    }

    @Transactional
    public void restoreInheritedItem(SimpleUser user, Long branchId, Long parentItemId) {
        Branch branch = getBranchForUser(user, branchId);
        MenuItem parent = menuItemRepository.findById(parentItemId)
                .orElseThrow(() -> new RuntimeException("Parent item not found"));
        branchMenuItemRepository.findByBranchAndParentItem(branch, parent)
                .ifPresent(branchMenuItemRepository::delete);
    }

    @Transactional
    public void deleteBranchMenuItem(SimpleUser user, Long branchId, Long branchMenuItemId) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(branchMenuItemId, branch)
                .orElseThrow(() -> new SecurityException("Branch menu item not found"));
        branchMenuItemRepository.delete(bmi);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Restaurant getOwnedRestaurant(SimpleUser user, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (!restaurant.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Restaurant not owned by user");
        }
        return restaurant;
    }

    // ── EffectiveMenuItem DTO ─────────────────────────────────────────────────

    public static class EffectiveMenuItem {
        private Long restaurantItemId;
        private Long branchItemId;
        private String name;
        private String description;
        private BigDecimal price;
        private String category;
        private boolean overridden;
        private boolean branchOnly;

        public Long getRestaurantItemId()             { return restaurantItemId; }
        public void setRestaurantItemId(Long id)      { this.restaurantItemId = id; }
        public Long getBranchItemId()                 { return branchItemId; }
        public void setBranchItemId(Long id)          { this.branchItemId = id; }
        public String getName()                       { return name; }
        public void setName(String name)              { this.name = name; }
        public String getDescription()                { return description; }
        public void setDescription(String d)          { this.description = d; }
        public BigDecimal getPrice()                  { return price; }
        public void setPrice(BigDecimal price)        { this.price = price; }
        public String getCategory()                   { return category; }
        public void setCategory(String category)      { this.category = category; }
        public boolean isOverridden()                 { return overridden; }
        public void setOverridden(boolean overridden) { this.overridden = overridden; }
        public boolean isBranchOnly()                 { return branchOnly; }
        public void setBranchOnly(boolean branchOnly) { this.branchOnly = branchOnly; }
    }
}