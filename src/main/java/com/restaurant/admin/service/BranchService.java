package com.restaurant.admin.service;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BranchService {
    
    @Autowired
    private BranchRepository branchRepository;
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    /**
     * Create a new branch for a restaurant (owned by user)
     * Security: Verifies user owns the restaurant
     */
    @Transactional
    public Branch createBranch(SimpleUser user, String branchName, String address, String phone) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        
        Branch branch = new Branch(branchName, address, phone, restaurant);
        branch.setCreatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }
    
    /**
     * Get all branches for a user's restaurant (active only)
     * Security: Verifies user owns the restaurant
     */
    public List<Branch> getActiveBranchesForUser(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.findByRestaurantAndIsActiveTrue(restaurant);
    }
    
    /**
     * Get all branches for a user's restaurant (including inactive)
     * Security: Verifies user owns the restaurant
     */
    public List<Branch> getAllBranchesForUser(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.findByRestaurant(restaurant);
    }
    
    /**
     * Get a specific branch by ID if user owns it
     * Security: Verifies the branch belongs to user's restaurant
     */
    public Optional<Branch> getBranchForUser(SimpleUser user, Long branchId) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        return branchRepository.findByIdAndRestaurant(branchId, restaurant);
    }
    
    /**
     * Update a branch (owned by user)
     * Security: Verifies user owns the restaurant the branch belongs to
     */
    @Transactional
    public Branch updateBranch(SimpleUser user, Long branchId, String branchName, String address, String phone) {
        Branch branch = getBranchForUser(user, branchId)
                .orElseThrow(() -> new SecurityException("Branch not found or not owned by user"));
        
        branch.setBranchName(branchName);
        branch.setAddress(address);
        branch.setPhone(phone);
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }
    
    /**
     * Toggle branch active status
     * Security: Verifies user owns the restaurant the branch belongs to
     */
    @Transactional
    public Branch toggleBranchStatus(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId)
                .orElseThrow(() -> new SecurityException("Branch not found or not owned by user"));
        
        branch.setActive(!branch.isActive());
        branch.setUpdatedAt(LocalDateTime.now());
        return branchRepository.save(branch);
    }
    
    /**
     * Delete a branch (owned by user)
     * Security: Verifies user owns the restaurant the branch belongs to
     */
    @Transactional
    public void deleteBranch(SimpleUser user, Long branchId) {
        Branch branch = getBranchForUser(user, branchId)
                .orElseThrow(() -> new SecurityException("Branch not found or not owned by user"));
        branchRepository.delete(branch);
    }
    
    /**
     * Check if a user's restaurant is multi-branch
     */
    public boolean isMultiBranch(SimpleUser user) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new SecurityException("User has no restaurant"));
        long branchCount = branchRepository.countByRestaurant(restaurant);
        return branchCount > 1;
    }
}
