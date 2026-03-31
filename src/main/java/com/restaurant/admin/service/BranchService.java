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

    @Autowired private BranchRepository            branchRepository;
    @Autowired private RestaurantRepository        restaurantRepository;
    @Autowired private MenuItemRepository          menuItemRepository;
    @Autowired private BranchMenuItemRepository    branchMenuItemRepository;
    @Autowired private MenuItemImageStorageService imageStorageService;

    // ── Main branch auto-creation ─────────────────────────────────────────────

    @Transactional
    public Branch ensureMainBranch(Restaurant restaurant) {
        return branchRepository.findFirstByRestaurantAndIsMainBranchTrue(restaurant)
                .orElseGet(() -> {
                    Branch main = new Branch("Main Branch", restaurant, true);
                    return branchRepository.save(main);
                });
    }

    // ── Branch CRUD ───────────────────────────────────────────────────────────

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
            snapshot.setParentItem(null);
            snapshot.setHidden(false);
            snapshot.setName(item.getItemName());
            snapshot.setDescription(item.getItemDescription());
            snapshot.setPrice(item.getItemPrice() != null ? item.getItemPrice().doubleValue() : null);
            snapshot.setCategory(item.getCategory());
            // Copy photo path so snapshot items show images immediately
            snapshot.setPhotoPath(item.getPhotoPath());
            branchMenuItemRepository.save(snapshot);
        }
        return branch;
    }

    public List<Branch> getBranchesForRestaurant(SimpleUser user, Long restaurantId) {
        Restaurant restaurant = getOwnedRestaurant(user, restaurantId);
        ensureMainBranch(restaurant);
        return branchRepository.findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(restaurant);
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
        try { return Optional.of(getBranchForUser(user, branchId)); }
        catch (SecurityException e) { return Optional.empty(); }
    }

    @Transactional
    public Branch renameBranch(SimpleUser user, Long branchId, String newName) {
        Branch branch = getBranchForUser(user, branchId);
        branch.setBranchName(newName);
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch updateBranch(SimpleUser user, Long branchId, String branchName,
                               @SuppressWarnings("unused") String address,
                               @SuppressWarnings("unused") String phone) {
        return renameBranch(user, branchId, branchName);
    }

    @Transactional
    public Branch toggleBranchStatus(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        if (branch.isMainBranch()) throw new SecurityException("Cannot deactivate the main branch");
        branch.setActive(!branch.isActive());
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }

    @Transactional
    public void deleteBranch(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        if (branch.isMainBranch()) throw new SecurityException("Cannot delete the main branch");
        branchMenuItemRepository.deleteAllByBranch(branch);
        branchRepository.delete(branch);
    }

    public boolean isMultiBranch(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.countByRestaurant(restaurant) > 1;
    }

    // ── Menu resolution ───────────────────────────────────────────────────────

    public List<EffectiveMenuItem> getEffectiveMenu(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        return buildEffectiveMenu(branch);
    }

    public List<EffectiveMenuItem> buildEffectiveMenu(Branch branch) {
        return branch.isMainBranch()
                ? buildMainBranchMenu(branch)
                : buildSnapshotMenu(branch);
    }

    /** Main branch reads live from the restaurant MenuItem table. */
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
            // ✅ Resolve full S3 / public URL for photo
            emi.setPhotoUrl(imageStorageService.toPublicUrl(item.getPhotoPath()));
            emi.setOverridden(false);
            emi.setBranchOnly(false);
            result.add(emi);
        }
        return result;
    }

    /** Non-main branches read their own independent snapshot rows. */
    private List<EffectiveMenuItem> buildSnapshotMenu(Branch branch) {
        List<BranchMenuItem> rows = branchMenuItemRepository.findByBranchAndHiddenFalse(branch);
        List<EffectiveMenuItem> result = new ArrayList<>();
        for (BranchMenuItem bmi : rows) {
            EffectiveMenuItem emi = new EffectiveMenuItem();
            emi.setBranchItemId(bmi.getId());
            emi.setRestaurantItemId(null);
            emi.setName(bmi.getName());
            emi.setDescription(bmi.getDescription());
            emi.setPrice(bmi.getPrice() != null ? BigDecimal.valueOf(bmi.getPrice()) : null);
            emi.setCategory(bmi.getCategory());
            // ✅ Resolve full S3 / public URL for photo
            emi.setPhotoUrl(imageStorageService.toPublicUrl(bmi.getPhotoPath()));
            emi.setOverridden(false);
            emi.setBranchOnly(true);
            result.add(emi);
        }
        return result;
    }

    // ── Branch menu item operations ───────────────────────────────────────────

    /**
     * Add a branch-only item, with optional photo upload to S3.
     */
    @Transactional
    public BranchMenuItem addBranchOnlyItem(SimpleUser user, Long branchId,
                                            String name, String description,
                                            Double price, String category,
                                            org.springframework.web.multipart.MultipartFile photo) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.setBranch(branch);
        bmi.setParentItem(null);
        bmi.setHidden(false);
        bmi.setName(name);
        bmi.setDescription(description);
        bmi.setPrice(price);
        bmi.setCategory(category);

        // ✅ Upload photo to S3 if provided
        if (photo != null && !photo.isEmpty()) {
            try {
                String photoUrl = imageStorageService.storePhoto(photo);
                bmi.setPhotoPath(photoUrl);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload photo: " + e.getMessage(), e);
            }
        }

        return branchMenuItemRepository.save(bmi);
    }

    /** Overload without photo — kept for callers that don't have a file. */
    @Transactional
    public BranchMenuItem addBranchOnlyItem(SimpleUser user, Long branchId,
                                            String name, String description,
                                            Double price, String category) {
        return addBranchOnlyItem(user, branchId, name, description, price, category, null);
    }

    /**
     * Edit an item — works for both main branch (restaurant MenuItem)
     * and snapshot branches (BranchMenuItem rows).
     * Photo upload is handled in the controller layer via MenuItemService / this method.
     */
    @Transactional
    public BranchMenuItem editBranchItem(SimpleUser user, Long branchId,
                                         Long itemId,
                                         String name, String description,
                                         Double price, String category,
                                         org.springframework.web.multipart.MultipartFile photo) {
        Branch branch = getBranchForUser(user, branchId);

        BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                .orElseThrow(() -> new RuntimeException("Item not found in this branch"));
        bmi.setName(name);
        bmi.setDescription(description);
        bmi.setPrice(price);
        bmi.setCategory(category);

        if (photo != null && !photo.isEmpty()) {
            try {
                // Delete old photo from S3 if it exists
                if (bmi.getPhotoPath() != null) {
                    imageStorageService.deleteIfExists(bmi.getPhotoPath());
                }
                String photoUrl = imageStorageService.storePhoto(photo);
                bmi.setPhotoPath(photoUrl);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload photo: " + e.getMessage(), e);
            }
        }

        return branchMenuItemRepository.save(bmi);
    }

    /**
     * Unified edit — routes to the right path based on branch type.
     * For main branch: delegates to MenuItemService (handled in controller).
     * For snapshot branches: updates BranchMenuItem row directly.
     */
    @Transactional
    public void editItem(SimpleUser user, Long branchId, Long itemId,
                         String name, String description, Double price, String category) {
        Branch branch = getBranchForUser(user, branchId);

        if (branch.isMainBranch()) {
            // Main branch items are real MenuItems — update via MenuItemService
            // Controller handles this path directly
            MenuItem parent = menuItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            parent.setItemName(name);
            parent.setItemDescription(description);
            if (price != null) parent.setItemPrice(BigDecimal.valueOf(price));
            parent.setCategory(category);
            menuItemRepository.save(parent);
        } else {
            // Snapshot branch — update BranchMenuItem row
            BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            bmi.setName(name);
            bmi.setDescription(description);
            bmi.setPrice(price);
            bmi.setCategory(category);
            branchMenuItemRepository.save(bmi);
        }
    }

    @Transactional
    public void deleteItem(SimpleUser user, Long branchId, Long itemId) {
        Branch branch = getBranchForUser(user, branchId);

        if (branch.isMainBranch()) {
            MenuItem item = menuItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            // Delete photo from S3
            imageStorageService.deleteIfExists(item.getPhotoPath());
            menuItemRepository.delete(item);
        } else {
            BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            imageStorageService.deleteIfExists(bmi.getPhotoPath());
            branchMenuItemRepository.delete(bmi);
        }
    }

    // ── Legacy compatibility methods ──────────────────────────────────────────

    @Transactional
    public BranchMenuItem overrideInheritedItem(SimpleUser user, Long branchId, Long parentItemId,
                                                String name, String description,
                                                Double price, String category) {
        editItem(user, branchId, parentItemId, name, description, price, category);
        return null;
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
        imageStorageService.deleteIfExists(bmi.getPhotoPath());
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
        private String photoUrl;      // ✅ full S3 / public URL
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
        public void setCategory(String cat)           { this.category = cat; }
        public String getPhotoUrl()                   { return photoUrl; }   // ✅
        public void setPhotoUrl(String photoUrl)      { this.photoUrl = photoUrl; }
        public boolean isOverridden()                 { return overridden; }
        public void setOverridden(boolean overridden) { this.overridden = overridden; }
        public boolean isBranchOnly()                 { return branchOnly; }
        public void setBranchOnly(boolean branchOnly) { this.branchOnly = branchOnly; }
    }
}