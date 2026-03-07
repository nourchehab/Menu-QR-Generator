package com.restaurant.admin.repository;

import com.restaurant.admin.model.Tag;
import com.restaurant.admin.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByRestaurantAndNameIgnoreCase(Restaurant restaurant, String name);
    List<Tag> findByRestaurant(Restaurant restaurant);
}
