package com.restaurant.admin.repository;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByRestaurantAndIsActiveTrue(Restaurant restaurant);

    List<Branch> findByRestaurant(Restaurant restaurant);

    /** Main branch always first, then by creation date ascending. */
    List<Branch> findByRestaurantOrderByIsMainBranchDescCreatedAtAsc(Restaurant restaurant);

    Optional<Branch> findByIdAndRestaurant(Long id, Restaurant restaurant);

    Optional<Branch> findFirstByRestaurantAndIsMainBranchTrue(Restaurant restaurant);

    long countByRestaurant(Restaurant restaurant);
}