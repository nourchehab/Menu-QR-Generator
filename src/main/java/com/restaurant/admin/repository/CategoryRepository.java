package com.restaurant.admin.repository;

import com.restaurant.admin.model.Category;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByRestaurant(Restaurant restaurant);
    Optional<Category> findByRestaurantAndNameIgnoreCase(Restaurant restaurant, String name);
}
