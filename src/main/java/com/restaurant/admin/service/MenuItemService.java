package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class MenuItemService {
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    @Value("${file.upload.photo-dir:uploads/photos}")
    private String photoUploadDir;
    
    /**
     * Add a new menu item
     */
    @Transactional
    public MenuItem addMenuItem(Long restaurantId, String itemName, BigDecimal itemPrice, 
                               String itemDescription, MultipartFile photoFile) throws IOException {
        
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        
        MenuItem menuItem = new MenuItem(itemName, itemPrice, itemDescription);
        menuItem.setRestaurant(restaurant);
        
        // Handle photo upload if provided
        if (photoFile != null && !photoFile.isEmpty()) {
            String photoPath = savePhoto(photoFile);
            menuItem.setPhotoPath(photoPath);
        }
        
        return menuItemRepository.save(menuItem);
    }
    
    /**
     * Save photo file to disk
     */
    private String savePhoto(MultipartFile file) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(photoUploadDir);
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
        
        return uniqueFilename;
    }
    
    /**
     * Get all menu items for a restaurant
     */
    public List<MenuItem> getMenuItemsByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }
    
    /**
     * Get menu item by ID
     */
    public MenuItem getMenuItemById(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
    }
    
    /**
     * Update menu item
     */
    @Transactional
    public MenuItem updateMenuItem(Long id, String itemName, BigDecimal itemPrice, 
                                  String itemDescription, MultipartFile photoFile) throws IOException {
        
        MenuItem menuItem = getMenuItemById(id);
        
        menuItem.setItemName(itemName);
        menuItem.setItemPrice(itemPrice);
        menuItem.setItemDescription(itemDescription);
        
        // Update photo if new one provided
        if (photoFile != null && !photoFile.isEmpty()) {
            String photoPath = savePhoto(photoFile);
            menuItem.setPhotoPath(photoPath);
        }
        
        return menuItemRepository.save(menuItem);
    }
    
    /**
     * Delete menu item
     */
    @Transactional
    public void deleteMenuItem(Long id) {
        menuItemRepository.deleteById(id);
    }
    
    /**
     * Get count of menu items for a restaurant
     */
    public long getMenuItemCount(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        return menuItemRepository.countByRestaurant(restaurant);
    }
}