package com.restaurant.admin.service;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
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

    // @Lazy to avoid circular dependency (BranchService → RestaurantService → BranchService)
    @Autowired
    @Lazy
    private BranchService branchService;

    /**
     * Create a new restaurant for a user, then auto-create the Main Branch.
     */
    @Transactional
    public Restaurant setupRestaurant(Long userId, String restaurantName,
                                      String restaurantType, MultipartFile logoFile) throws IOException {

        SimpleUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Restaurant restaurant = new Restaurant(restaurantName, restaurantType, user);

        if (logoFile != null && !logoFile.isEmpty()) {
            if (restaurant.getLogoPath() != null && !restaurant.getLogoPath().isBlank()) {
                s3PhotoStorageService.deleteIfS3Url(restaurant.getLogoPath());
            }
            String logoPath = s3PhotoStorageService.uploadNewLogo(logoFile);
            restaurant.setLogoPath(logoPath);
        }

        restaurant = restaurantRepository.save(restaurant);

        // ✅ Auto-create the Main Branch for every new restaurant
        branchService.ensureMainBranch(restaurant);

        user.setRestaurantSetupComplete(true);
        userRepository.save(user);

        return restaurant;
    }

    public Optional<Restaurant> getRestaurantByUser(SimpleUser user) {
        return restaurantRepository.findFirstByUserOrderByIdDesc(user);
    }

    public List<Restaurant> getRestaurantsByUser(SimpleUser user) {
        return restaurantRepository.findAllByUser(user);
    }

    public Optional<Restaurant> getRestaurantByUserId(Long userId) {
        return restaurantRepository.findFirstByUserIdOrderByIdDesc(userId);
    }

    public Optional<Restaurant> getRestaurantById(Long id) {
        return restaurantRepository.findById(id);
    }

    @Transactional
    public Restaurant updateMenuBackgroundColor(SimpleUser user, String hex) {
        Restaurant restaurant = restaurantRepository.findFirstByUserOrderByIdDesc(user)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        String safe = (hex == null || hex.isBlank()) ? "" : ColorContrastUtil.normalizeHex(hex);
        restaurant.setMenuBackgroundColor(safe);
        return restaurantRepository.save(restaurant);
    }

    public boolean userHasRestaurant(SimpleUser user) {
        return restaurantRepository.existsByUser(user);
    }

    public String toPublicLogoUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) return storedPath;
        if (storedPath.startsWith("/uploads/")) return storedPath;
        return "/uploads/logos/" + storedPath;
    }
}