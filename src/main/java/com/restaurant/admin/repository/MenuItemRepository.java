package com.restaurant.admin.repository;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    
    List<MenuItem> findByRestaurant(Restaurant restaurant);
    
    List<MenuItem> findByRestaurantId(Long restaurantId);
    
    long countByRestaurant(Restaurant restaurant);
}