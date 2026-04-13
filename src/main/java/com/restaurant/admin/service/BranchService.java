package com.restaurant.admin.service;

import com.restaurant.admin.model.*;
import com.restaurant.admin.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BranchService {

    @Autowired private BranchRepository            branchRepository;
    @Autowired private RestaurantRepository        restaurantRepository;
    @Autowired private BranchMenuItemRepository    branchMenuItemRepository;
    @Autowired private MenuItemImageStorageService imageStorageService;

    // ── Main branch auto-creation ─────────────────────────────────────────────

    @Transactional
    public Branch ensureMainBranch(Restaurant restaurant) {
        return branchRepository.findFirstByRestaurantAndIsMainBranchTrue(restaurant)
                .orElseGet(() -> branchRepository.save(new Branch("Main Branch", restaurant, true)));
    }

    // ── Branch CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public Branch createBranch(SimpleUser user, Long restaurantId, String branchName) {
        Restaurant restaurant = getOwnedRestaurant(user, restaurantId);
        Branch branch = new Branch(branchName, restaurant, false);
        branch = branchRepository.save(branch);

        // Snapshot current main branch items into the new branch
        Branch mainBranch = ensureMainBranch(restaurant);
        List<BranchMenuItem> mainItems = branchMenuItemRepository.findByBranchAndHiddenFalse(mainBranch);
        for (BranchMenuItem item : mainItems) {
            BranchMenuItem snapshot = new BranchMenuItem();
            snapshot.setBranch(branch);
            snapshot.setParentItem(null);
            snapshot.setHidden(false);
            snapshot.setName(item.getName());
            snapshot.setDescription(item.getDescription());
            snapshot.setPrice(item.getPrice());
            snapshot.setCategory(item.getCategory());
            snapshot.setPhotoPath(item.getPhotoPath()); // reference copy — not a new S3 object
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

    public Branch getBranchForUser(SimpleUser user, Long branchId) {
        // ✅ FIXED: was findByIdWithRestaurant which doesn't exist in BranchRepository
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new SecurityException("Branch not found"));
        if (!branch.getRestaurant().getUser().getId().equals(user.getId()))
            throw new SecurityException("Branch not owned by user");
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

    public List<Branch> getNonMainBranchesForRestaurant(SimpleUser user, Long restaurantId) {
        Restaurant restaurant = getOwnedRestaurant(user, restaurantId);
        return branchRepository.findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(restaurant)
                .stream().filter(b -> !b.isMainBranch()).toList();
    }

    // ── Menu resolution ───────────────────────────────────────────────────────

    public List<EffectiveMenuItem> getEffectiveMenu(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId);
        return buildEffectiveMenu(branch);
    }

    /**
     * All branches (including main) read from BranchMenuItem table only.
     */
    public List<EffectiveMenuItem> buildEffectiveMenu(Branch branch) {
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
            emi.setPhotoUrl(imageStorageService.toPublicUrl(bmi.getPhotoPath()));
            emi.setOverridden(false);
            emi.setBranchOnly(!branch.isMainBranch());
            result.add(emi);
        }
        return result;
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    /**
     * ALL branches (including main) store items in BranchMenuItem only.
     * No MenuItem table involved.
     */
    @Transactional
    public BranchMenuItem addItem(SimpleUser user, Long branchId,
                                  String name, String description,
                                  Double price, String category,
                                  MultipartFile photo) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = new BranchMenuItem();
        bmi.setBranch(branch);
        bmi.setParentItem(null);
        bmi.setHidden(false);
        bmi.setName(name);
        bmi.setDescription(description);
        bmi.setPrice(price);
        bmi.setCategory(category);
        if (photo != null && !photo.isEmpty()) {
            try { bmi.setPhotoPath(imageStorageService.storePhoto(photo)); }
            catch (Exception e) { throw new RuntimeException("Failed to upload photo: " + e.getMessage(), e); }
        }
        return branchMenuItemRepository.save(bmi);
    }

    /** Overload without photo. */
    @Transactional
    public BranchMenuItem addItem(SimpleUser user, Long branchId,
                                  String name, String description,
                                  Double price, String category) {
        return addItem(user, branchId, name, description, price, category, null);
    }

    // ── Edit item ─────────────────────────────────────────────────────────────

    /**
     * All branches edit BranchMenuItem rows. No MenuItem table involved.
     */
    @Transactional
    public BranchMenuItem editItem(SimpleUser user, Long branchId, Long itemId,
                                   String name, String description,
                                   Double price, String category,
                                   MultipartFile photo) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                .orElseThrow(() -> new RuntimeException("Item not found in this branch"));
        bmi.setName(name);
        bmi.setDescription(description);
        bmi.setPrice(price);
        bmi.setCategory(category);
        if (photo != null && !photo.isEmpty()) {
            try {
                if (bmi.getPhotoPath() != null) {
                    deletePhotoIfUnshared(bmi.getPhotoPath(), bmi.getId());
                }
                bmi.setPhotoPath(imageStorageService.storePhoto(photo));
            } catch (Exception e) { throw new RuntimeException("Failed to upload photo: " + e.getMessage(), e); }
        }
        return branchMenuItemRepository.save(bmi);
    }

    /** Overload without photo — used by text-only edits and categorise. */
    @Transactional
    public void editItem(SimpleUser user, Long branchId, Long itemId,
                         String name, String description, Double price, String category) {
        editItem(user, branchId, itemId, name, description, price, category, null);
    }

    /** Legacy name. */
    @Transactional
    public BranchMenuItem editBranchItem(SimpleUser user, Long branchId, Long itemId,
                                         String name, String description,
                                         Double price, String category,
                                         MultipartFile photo) {
        return editItem(user, branchId, itemId, name, description, price, category, photo);
    }

    // ── Delete item ───────────────────────────────────────────────────────────

    /**
     * Deletes a BranchMenuItem.
     * S3 photo is only deleted if NO other BranchMenuItem references the same path.
     * This protects snapshot branches that copied the same photo URL.
     */
    @Transactional
    public void deleteItem(SimpleUser user, Long branchId, Long itemId) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        String photoPath = bmi.getPhotoPath();
        branchMenuItemRepository.delete(bmi);

        if (photoPath != null) {
            deletePhotoIfUnshared(photoPath, null);
        }
    }

    // ── Copy item to selected branches ────────────────────────────────────────

    @Transactional
    public void copyItemToBranches(SimpleUser user, Long sourceItemId, List<Long> targetBranchIds) {
        BranchMenuItem source = branchMenuItemRepository.findById(sourceItemId)
                .orElseThrow(() -> new RuntimeException("Source item not found"));

        if (!source.getBranch().getRestaurant().getUser().getId().equals(user.getId()))
            throw new SecurityException("Not authorised");

        for (Long targetBranchId : targetBranchIds) {
            Branch target = branchRepository.findById(targetBranchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found: " + targetBranchId));

            if (!target.getRestaurant().getUser().getId().equals(user.getId())) continue;

            boolean exists = branchMenuItemRepository.findByBranch(target).stream()
                    .anyMatch(bmi -> source.getName().equalsIgnoreCase(bmi.getName()));
            if (exists) continue;

            BranchMenuItem copy = new BranchMenuItem();
            copy.setBranch(target);
            copy.setParentItem(null);
            copy.setHidden(false);
            copy.setName(source.getName());
            copy.setDescription(source.getDescription());
            copy.setPrice(source.getPrice());
            copy.setCategory(source.getCategory());
            copy.setPhotoPath(source.getPhotoPath()); // shared reference — safe
            branchMenuItemRepository.save(copy);
        }
    }

    // ── Delete item from selected branches ────────────────────────────────────

    /**
     * Removes BranchMenuItem rows matching itemName from the given branches.
     * Does NOT delete S3 photos.
     */
    @Transactional
    public void deleteItemFromBranches(SimpleUser user, String itemName, List<Long> targetBranchIds) {
        for (Long targetBranchId : targetBranchIds) {
            Branch branch = branchRepository.findById(targetBranchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found: " + targetBranchId));
            if (!branch.getRestaurant().getUser().getId().equals(user.getId())) continue;

            branchMenuItemRepository.findByBranch(branch).stream()
                    .filter(bmi -> itemName.equalsIgnoreCase(bmi.getName()))
                    .forEach(branchMenuItemRepository::delete);
        }
    }

    // ── Delete image only ─────────────────────────────────────────────────────

    /**
     * Removes the photo from a BranchMenuItem.
     * Only deletes from S3 if no other BranchMenuItem shares the same path.
     */
    @Transactional
    public void deleteItemImage(SimpleUser user, Long branchId, Long itemId) {
        Branch branch = getBranchForUser(user, branchId);
        BranchMenuItem bmi = branchMenuItemRepository.findByIdAndBranch(itemId, branch)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        String photoPath = bmi.getPhotoPath();
        bmi.setPhotoPath(null);
        branchMenuItemRepository.save(bmi);

        if (photoPath != null) {
            deletePhotoIfUnshared(photoPath, itemId);
        }
    }

    // ── Photo safety helper ───────────────────────────────────────────────────

    /**
     * Deletes a photo from S3 only if no other BranchMenuItem (excluding excludeItemId)
     * still references the same photoPath.
     */
    private void deletePhotoIfUnshared(String photoPath, Long excludeItemId) {
        long refCount = branchMenuItemRepository.countByPhotoPath(photoPath);
        long threshold = excludeItemId != null ? 1 : 0;
        if (refCount <= threshold) {
            imageStorageService.deleteIfExists(photoPath);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Restaurant getOwnedRestaurant(SimpleUser user, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (!restaurant.getUser().getId().equals(user.getId()))
            throw new SecurityException("Restaurant not owned by user");
        return restaurant;
    }

    // ── Legacy methods (kept for BranchMenuController compatibility) ─────────

    @Transactional
    public void hideInheritedItem(SimpleUser user, Long branchId, Long itemId) {
        deleteItem(user, branchId, itemId);
    }

    @Transactional
    public void restoreInheritedItem(SimpleUser user, Long branchId, Long itemId) {
        // No-op: flat model has no hidden/parent concept.
    }

    @Transactional
    public BranchMenuItem addBranchOnlyItem(SimpleUser user, Long branchId,
                                            String name, String description,
                                            Double price, String category,
                                            MultipartFile photo) {
        return addItem(user, branchId, name, description, price, category, photo);
    }

    @Transactional
    public BranchMenuItem addBranchOnlyItem(SimpleUser user, Long branchId,
                                            String name, String description,
                                            Double price, String category) {
        return addItem(user, branchId, name, description, price, category, null);
    }

    // ── AI Categorisation helpers (used by RestaurantController) ─────────────

    public BranchMenuItem getBranchMenuItemById(Long itemId, Long branchId) {
        return branchRepository.findById(branchId)
                .flatMap(branch -> branchMenuItemRepository.findByIdAndBranch(itemId, branch))
                .orElse(null);
    }

    @Transactional
    public BranchMenuItem saveBranchMenuItem(BranchMenuItem item) {
        return branchMenuItemRepository.save(item);
    }

    // ── EffectiveMenuItem DTO ─────────────────────────────────────────────────

    public static class EffectiveMenuItem {
        private Long restaurantItemId;
        private Long branchItemId;
        private String name;
        private String description;
        private BigDecimal price;
        private String category;
        private String photoUrl;
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
        public String getPhotoUrl()                   { return photoUrl; }
        public void setPhotoUrl(String photoUrl)      { this.photoUrl = photoUrl; }
        public boolean isOverridden()                 { return overridden; }
        public void setOverridden(boolean overridden) { this.overridden = overridden; }
        public boolean isBranchOnly()                 { return branchOnly; }
        public void setBranchOnly(boolean branchOnly) { this.branchOnly = branchOnly; }
    }
}