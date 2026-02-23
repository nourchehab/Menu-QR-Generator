package com.restaurant.admin.service;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import com.restaurant.admin.util.ColorContrastUtil;

@Service
public class RestaurantService {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private SimpleUserRepository userRepository;

    @Value("${file.upload.logo-dir:uploads/logos}")
    private String logoUploadDir;

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
            String logoPath = saveLogo(logoFile);
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
     * Save logo file to disk
     */
    private String saveLogo(MultipartFile file) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(logoUploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // Save file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return uniqueFilename; // Return just the filename, not full path
    }

    /**
     * Get restaurant by user
     */
    public Optional<Restaurant> getRestaurantByUser(SimpleUser user) {
        return restaurantRepository.findByUser(user);
    }

    /**
     * Public lookup by restaurant id (used by QR/public menu pages).
     */
    public Optional<Restaurant> getRestaurantById(Long restaurantId) {
        return restaurantRepository.findById(restaurantId);
    }

    /**
     * Update the menu background color for the current user's restaurant.
     */
    @Transactional
    public Restaurant updateMenuBackgroundColor(SimpleUser user, String hexColor) {
        Restaurant restaurant = restaurantRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Restaurant not found. Please complete setup first."));

        String normalized = ColorContrastUtil.normalizeHex(hexColor);
        restaurant.setMenuBackgroundColor(normalized);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Get restaurant by user ID
     */
    public Optional<Restaurant> getRestaurantByUserId(Long userId) {
        return restaurantRepository.findByUserId(userId);
    }

    /**
     * Check if user has a restaurant
     */
    public boolean userHasRestaurant(SimpleUser user) {
        return restaurantRepository.existsByUser(user);
    }

    /**
     * Get logo path for serving
     */
    public String getLogoPath(String filename) {
        return logoUploadDir + "/" + filename;
    }
}