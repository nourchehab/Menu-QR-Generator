package com.restaurant.admin.service;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import com.restaurant.admin.util.ColorContrastUtil;

@Service
public class RestaurantService {
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    @Autowired
    private SimpleUserRepository userRepository;

    @Autowired
    private S3PhotoStorageService s3PhotoStorageService;
    
    /**
     * Create or update restaurant setup for a user
     */
    @Transactional
    public Restaurant setupRestaurant(Long userId, String restaurantName, 
                                     String restaurantType, MultipartFile logoFile) throws IOException {
        
        SimpleUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Check if restaurant already exists for this user
        Optional<Restaurant> existingRestaurant = restaurantRepository.findByUser(user);
        
        Restaurant restaurant;
        if (existingRestaurant.isPresent()) {
            // Update existing restaurant
            restaurant = existingRestaurant.get();
            restaurant.setRestaurantName(restaurantName);
            restaurant.setRestaurantType(restaurantType);
        } else {
            // Create new restaurant
            restaurant = new Restaurant(restaurantName, restaurantType, user);
        }

        
        // Handle logo upload if provided
        if (logoFile != null && !logoFile.isEmpty()) {
            if (restaurant.getLogoPath() != null && !restaurant.getLogoPath().isBlank()) {
                s3PhotoStorageService.deleteIfS3Url(restaurant.getLogoPath());
            }
            String logoPath = s3PhotoStorageService.uploadNewLogo(logoFile);
            restaurant.setLogoPath(logoPath);
        }
        
        // Save restaurant
        restaurant = restaurantRepository.save(restaurant);
        
        // Mark user's restaurant setup as complete
        user.setRestaurantSetupComplete(true);
        userRepository.save(user);
        
        return restaurant;
    }
    
    /**
     * Get restaurant by user
     */
    public Optional<Restaurant> getRestaurantByUser(SimpleUser user) {
        return restaurantRepository.findByUser(user);
    }
    
    /**
     * Get restaurant by user ID
     */
    public Optional<Restaurant> getRestaurantByUserId(Long userId) {
        return restaurantRepository.findByUserId(userId);
    }

    /**
     * Get restaurant by id
     */
    public Optional<Restaurant> getRestaurantById(Long id) {
        return restaurantRepository.findById(id);
    }

    /**
     * Update menu background color for a user's restaurant
     */
    @Transactional
    public Restaurant updateMenuBackgroundColor(SimpleUser user, String hex) {
        Restaurant restaurant = restaurantRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        String safe = (hex == null || hex.isBlank()) ? "" : ColorContrastUtil.normalizeHex(hex);
        restaurant.setMenuBackgroundColor(safe);
        return restaurantRepository.save(restaurant);
    }

    
    /**
     * Check if user has a restaurant
     */
    public boolean userHasRestaurant(SimpleUser user) {
        return restaurantRepository.existsByUser(user);
    }

    
    public String toPublicLogoUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            return storedPath;
        }
        if (storedPath.startsWith("/uploads/")) {
            return storedPath;
        }
        return "/uploads/logos/" + storedPath;
    }
}