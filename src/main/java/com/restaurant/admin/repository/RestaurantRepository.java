package com.restaurant.admin.repository;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    
    Optional<Restaurant> findByUser(SimpleUser user);
    
    Optional<Restaurant> findByUserId(Long userId);
    
    boolean existsByUser(SimpleUser user);

    List<Restaurant> findAllByUser(SimpleUser user);
}