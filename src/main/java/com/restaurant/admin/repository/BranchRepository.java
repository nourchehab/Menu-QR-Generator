package com.restaurant.admin.repository;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select b from Branch b left join fetch b.restaurant where b.id = :id")
    Optional<Branch> findByIdWithRestaurant(@Param("id") Long id);

    @Query("select b from Branch b left join fetch b.restaurant r left join fetch r.user where b.id = :id")
    Optional<Branch> findByIdWithRestaurantAndUser(@Param("id") Long id);

    Optional<Branch> findFirstByRestaurantAndIsMainBranchTrue(Restaurant restaurant);

    long countByRestaurant(Restaurant restaurant);
}