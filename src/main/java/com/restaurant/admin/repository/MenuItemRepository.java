package com.restaurant.admin.repository;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    
    List<MenuItem> findByRestaurant(Restaurant restaurant);
    
    List<MenuItem> findByRestaurantId(Long restaurantId);

    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);
    @org.springframework.data.jpa.repository.Query("select m from MenuItem m left join fetch m.restaurant where m.id = :id")
    Optional<MenuItem> findByIdWithRestaurant(Long id);
    
    long countByRestaurant(Restaurant restaurant);
}