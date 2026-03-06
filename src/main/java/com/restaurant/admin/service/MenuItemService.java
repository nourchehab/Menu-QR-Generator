package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
public class MenuItemService {
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuItemImageStorageService imageStorageService;
    
    /**
     * Add a new menu item
     */
    @Transactional
    public MenuItem addMenuItem(Long restaurantId, String itemName, BigDecimal itemPrice,
                               String itemDescription, MultipartFile photoFile) throws IOException {
        return addMenuItem(restaurantId, itemName, itemPrice, itemDescription, photoFile, null);
    }

    /**
     * Add a new menu item with category (used for image organisation).
     */
    @Transactional
    public MenuItem addMenuItem(Long restaurantId, String itemName, BigDecimal itemPrice,
                               String itemDescription, MultipartFile photoFile,
                               String category) throws IOException {

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        MenuItem menuItem = new MenuItem(itemName, itemPrice, itemDescription);
        menuItem.setRestaurant(restaurant);
        if (category != null && !category.isBlank()) {
            menuItem.setCategory(category);
        }

        // Handle photo upload if provided
        if (photoFile != null && !photoFile.isEmpty()) {
            com.restaurant.admin.image.ProcessedImageResult result =
                    imageStorageService.storeWithVariants(photoFile, category);
            menuItem.setPhotoPath(result.displayFilename());
            menuItem.setThumbPath(result.thumbFilename());
        }

        return menuItemRepository.save(menuItem);
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
            imageStorageService.deleteIfExists(menuItem.getPhotoPath());
            imageStorageService.deleteIfExists(menuItem.getThumbPath());
            com.restaurant.admin.image.ProcessedImageResult result =
                    imageStorageService.storeWithVariants(photoFile, menuItem.getCategory());
            menuItem.setPhotoPath(result.displayFilename());
            menuItem.setThumbPath(result.thumbFilename());
        }

        return menuItemRepository.save(menuItem);
    }

    /**
     * Upload/replace only the image for a menu item.
     */
    @Transactional
    public MenuItem uploadMenuItemImage(Long id, MultipartFile photoFile) throws IOException {
        MenuItem menuItem = getMenuItemById(id);
        imageStorageService.deleteIfExists(menuItem.getPhotoPath());
        imageStorageService.deleteIfExists(menuItem.getThumbPath());
        com.restaurant.admin.image.ProcessedImageResult result =
                imageStorageService.storeWithVariants(photoFile, menuItem.getCategory());
        menuItem.setPhotoPath(result.displayFilename());
        menuItem.setThumbPath(result.thumbFilename());
        return menuItemRepository.save(menuItem);
    }

    /**
     * Delete only the image for a menu item.
     */
    @Transactional
    public MenuItem deleteMenuItemImage(Long id) {
        MenuItem menuItem = getMenuItemById(id);
        imageStorageService.deleteIfExists(menuItem.getPhotoPath());
        imageStorageService.deleteIfExists(menuItem.getThumbPath());
        menuItem.setPhotoPath(null);
        menuItem.setThumbPath(null);
        return menuItemRepository.save(menuItem);
    }
    
    /**
     * Delete menu item
     */
    @Transactional
    public void deleteMenuItem(Long id) {
        MenuItem menuItem = getMenuItemById(id);
        imageStorageService.deleteIfExists(menuItem.getPhotoPath());
        menuItemRepository.delete(menuItem);
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