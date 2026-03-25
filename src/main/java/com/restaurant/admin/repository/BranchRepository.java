package com.restaurant.admin.repository;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    
    /**
     * Find all active branches for a restaurant
     */
    List<Branch> findByRestaurantAndIsActiveTrue(Restaurant restaurant);
    
    /**
     * Find all branches (active or inactive) for a restaurant
     */
    List<Branch> findByRestaurant(Restaurant restaurant);
    
    /**
     * Find a branch by ID and verify it belongs to a specific restaurant
     */
    Optional<Branch> findByIdAndRestaurant(Long id, Restaurant restaurant);
    
    /**
     * Count branches for a restaurant
     */
    long countByRestaurant(Restaurant restaurant);
}
