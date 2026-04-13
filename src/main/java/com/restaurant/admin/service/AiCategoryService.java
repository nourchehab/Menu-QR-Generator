package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.BranchMenuItem;
import com.restaurant.admin.model.Category;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.BranchMenuItemRepository;
import com.restaurant.admin.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to integrate AI categorization with menu item management
 * Orchestrates calls to the AI service and saves results
 */
@Service
public class AiCategoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(AiCategoryService.class);
    
    @Autowired
    private AiServiceClient aiServiceClient;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private BranchMenuItemRepository branchMenuItemRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private com.restaurant.admin.repository.RestaurantRepository restaurantRepository;
    
    /**
     * Get AI categorization for a menu item and save to database
     * 
     * @param menuItem The menu item to categorize
     * @param restaurantId Restaurant ID for AI context
     * @param branchId Branch ID for AI context (each branch has separate categorization)
     * @return true if successful, false if AI service unavailable
     */
    @Transactional
    public boolean categorizeAndSaveMenuItem(MenuItem menuItem, Long restaurantId, String branchId) {
        try {
            // Call AI service
            AiServiceClient.CategorizeResponse response = aiServiceClient.categorizeMenuItem(
                menuItem.getItemName(),
                menuItem.getItemDescription(),
                menuItem.getItemPrice().doubleValue(),
                restaurantId.toString(),
                branchId != null ? branchId : "default"
            );
            
            if (response == null) {
                logger.warn("AI service returned null response for item: {}", menuItem.getItemName());
                return false;
            }
            
            // Save AI response to menu item
            menuItem.setSuggestedCategory(response.category);
            menuItem.setAiConfidence(response.confidence);
            // Truncate reasoning to fit in database (max 990 chars to be safe)
            String reasoning = response.reasoning;
            if (reasoning != null && reasoning.length() > 990) {
                reasoning = reasoning.substring(0, 987) + "...";
            }
            menuItem.setAiReasoning(reasoning);
            menuItem.setAiAnalyzedAt(LocalDateTime.now());
            
            // Persist to database
            try {
                menuItemRepository.save(menuItem);
                logger.info("Successfully categorized item '{}' -> '{}' (confidence: {}%)", 
                    menuItem.getItemName(), response.category, (response.confidence * 100));
                return true;
            } catch (Exception dbError) {
                logger.error("Database error saving categorization for item '{}': {}", 
                    menuItem.getItemName(), dbError.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during AI categorization: ", e);
            return false;
        }
    }
    
    /**
     * Batch categorize multiple menu items for a restaurant branch
     * 
     * @param menuItems List of menu items to categorize
     * @param restaurantId Restaurant ID
     * @param branchId Branch ID
     * @return Number of successfully categorized items
     */
    @Transactional
    public int categorizeMenuItemsBatch(java.util.List<MenuItem> menuItems, 
                                        Long restaurantId, String branchId) {
        int successCount = 0;
        for (MenuItem item : menuItems) {
            if (categorizeAndSaveMenuItem(item, restaurantId, branchId)) {
                successCount++;
            }
        }
        logger.info("Batch categorization complete: {}/{} items processed", 
            successCount, menuItems.size());
        return successCount;
    }
    
    /**
     * Accept an AI suggestion and apply it as the official category
     * Auto-creates category if it doesn't exist in the restaurant
     * 
     * @param menuItem Menu item that has been AI-categorized
     * @return true if successful
     */
    @Transactional
    public boolean acceptAiSuggestion(MenuItem menuItem) {
        if (menuItem.getSuggestedCategory() == null) {
            logger.warn("No AI suggestion to accept for item: {}", menuItem.getItemName());
            return false;
        }
        
        try {
            String suggestedCategoryName = menuItem.getSuggestedCategory();
            Long restId = menuItem.getRestaurant() != null ? menuItem.getRestaurant().getId() : null;
            if (restId == null) {
                logger.warn("MenuItem {} has no associated restaurant", menuItem.getId());
                return false;
            }
            Restaurant restaurant = restaurantRepository.findById(restId).orElseThrow(() -> new RuntimeException("Restaurant not found"));
            
            // Try to find or create the category
            Category category = findOrCreateCategory(restaurant, suggestedCategoryName);
            
            // Link category to menu item
            menuItem.setCategory(suggestedCategoryName);
            menuItem.setCategoryEntity(category);
            menuItemRepository.save(menuItem);
            
            logger.info("Accepted AI suggestion for item '{}' -> Category '{}' (ID: {})", 
                menuItem.getItemName(), category.getName(), category.getId());
            
            return true;
        } catch (Exception e) {
            logger.error("Error accepting AI suggestion: ", e);
            return false;
        }
    }
    
    /**
     * Find existing category or create a new one for the restaurant
     * This ensures we never have orphaned AI suggestions
     * 
     * @param restaurant Restaurant that needs the category
     * @param categoryName Name of category from AI
     * @return Category entity (existing or newly created)
     */
    @Transactional
    public Category findOrCreateCategory(Restaurant restaurant, String categoryName) {
        // Try to find existing category
        Optional<Category> existing = categoryRepository.findByRestaurantAndNameIgnoreCase(
            restaurant, categoryName);
        
        if (existing.isPresent()) {
            logger.debug("Found existing category: {}", categoryName);
            return existing.get();
        }
        
        // Create new category
        Category newCategory = new Category();
        newCategory.setName(categoryName);
        newCategory.setRestaurant(restaurant);
        newCategory.setCreatedAt(LocalDateTime.now());
        
        Category saved = categoryRepository.save(newCategory);
        logger.info("Auto-created new category '{}' for restaurant ID: {}", categoryName, restaurant.getId());
        
        return saved;
    }
    
    /**
     * Check if AI service is available
     */
    public boolean isAiServiceAvailable() {
        return aiServiceClient.isHealthy();
    }

    /**
     * Categorize a MenuItem and return detailed result for batch processing
     * For use with main branch items (restaurant-level)
     * 
     * @param menuItem The menu item to categorize
     * @param restaurantId Restaurant ID for AI context
     * @return result map with itemId, itemName, category, confidence, reasoning
     */
    @Transactional
    public Map<String, Object> categorizeAndSaveMenuItemForBatch(MenuItem menuItem, Long restaurantId) {
        try {
            String itemName = menuItem.getItemName();
            String itemDescription = menuItem.getItemDescription();
            Double itemPrice = menuItem.getItemPrice() != null ? menuItem.getItemPrice().doubleValue() : 0.0;
            
            if (itemName == null || itemName.trim().isEmpty()) {
                logger.warn("Cannot categorize: item name is empty");
                return Map.of(
                    "success", false,
                    "itemId", menuItem.getId(),
                    "itemName", itemName,
                    "category", "UNKNOWN",
                    "confidence", 0.0,
                    "reasoning", "Item name is empty"
                );
            }
            
            // Call AI service
            AiServiceClient.CategorizeResponse response = aiServiceClient.categorizeMenuItem(
                    itemName, 
                    itemDescription, 
                    itemPrice, 
                    restaurantId.toString(), 
                    "main"
            );
            
            String category = "UNKNOWN";
            Double confidence = 0.0;
            String reasoning = "No response from AI service";
            boolean success = false;
            
            if (response != null && response.category != null) {
                category = response.category;
                confidence = response.confidence != null ? response.confidence : 0.0;
                reasoning = response.reasoning != null ? response.reasoning : "";
                
                // Save category to MenuItem
                menuItem.setCategory(category);
                menuItem.setSuggestedCategory(response.category);
                menuItem.setAiConfidence(confidence);
                // Truncate reasoning to fit in database (max 990 chars)
                if (reasoning != null && reasoning.length() > 990) {
                    reasoning = reasoning.substring(0, 987) + "...";
                }
                menuItem.setAiReasoning(reasoning);
                menuItem.setAiAnalyzedAt(LocalDateTime.now());
                menuItemRepository.save(menuItem);
                success = true;
                
                logger.info("Categorized menu item '{}' -> '{}' (confidence: {})", 
                    itemName, category, confidence);
            }
            
            return Map.of(
                "success", success,
                "itemId", menuItem.getId(),
                "itemName", itemName,
                "category", category,
                "confidence", confidence,
                "reasoning", reasoning
            );
        } catch (Exception e) {
            logger.error("Error categorizing menu item: ", e);
            return Map.of(
                "success", false,
                "itemId", menuItem.getId(),
                "itemName", menuItem.getItemName(),
                "category", "ERROR",
                "confidence", 0.0,
                "reasoning", "Exception: " + e.getMessage()
            );
        }
    }

    /**
     * Categorize a BranchMenuItem using AI service
     * Works with items in branch_menu_items table
     * 
     * @param branchItem The branch menu item to categorize
     * @param restaurantId Restaurant ID for AI context
     * @param branchId Branch ID for AI context
     * @return result map with itemId, itemName, category, confidence, reasoning
     */
    @Transactional
    public Map<String, Object> categorizeAndSaveBranchMenuItem(BranchMenuItem branchItem, Long restaurantId, Long branchId) {
        try {
            // Extract item data for AI categorization
            String itemName = branchItem.getName();
            String itemDescription = branchItem.getDescription();
            Double itemPrice = branchItem.getPrice();
            
            if (itemName == null || itemName.trim().isEmpty()) {
                logger.warn("Cannot categorize: item name is empty");
                return Map.of(
                    "success", false,
                    "itemId", branchItem.getId(),
                    "itemName", itemName,
                    "category", "UNKNOWN",
                    "confidence", 0.0,
                    "reasoning", "Item name is empty"
                );
            }
            
            // Call AI service with item details
            AiServiceClient.CategorizeResponse response = aiServiceClient.categorizeMenuItem(
                    itemName, 
                    itemDescription, 
                    itemPrice != null ? itemPrice : 0.0, 
                    restaurantId.toString(), 
                    branchId.toString()
            );
            
            String category = "UNKNOWN";
            Double confidence = 0.0;
            String reasoning = "No response from AI service";
            boolean success = false;
            
            if (response != null && response.category != null) {
                category = response.category;
                confidence = response.confidence != null ? response.confidence : 0.0;
                reasoning = response.reasoning != null ? response.reasoning : "";
                
                // Save category to the BranchMenuItem
                branchItem.setCategory(category);
                branchMenuItemRepository.save(branchItem);
                success = true;
                
                logger.info("Categorized branch item '{}' -> '{}' (confidence: {})", 
                    itemName, category, confidence);
            }
            
            return Map.of(
                "success", success,
                "itemId", branchItem.getId(),
                "itemName", itemName,
                "category", category,
                "confidence", confidence,
                "reasoning", reasoning
            );
        } catch (Exception e) {
            logger.error("Error categorizing branch item: ", e);
            return Map.of(
                "success", false,
                "itemId", branchItem.getId(),
                "itemName", branchItem.getName(),
                "category", "ERROR",
                "confidence", 0.0,
                "reasoning", "Exception: " + e.getMessage()
            );
        }
    }

    /**
     * Batch categorize multiple BranchMenuItems
     * 
     * @param branchItems List of BranchMenuItems to categorize
     * @param restaurantId Restaurant ID for AI context
     * @param branchId Branch ID for AI context
     * @return List of categorization results with details
     */
    @Transactional
    public List<Map<String, Object>> categorizeBranchMenuItemsBatch(List<BranchMenuItem> branchItems, 
                                              Long restaurantId, Long branchId) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (BranchMenuItem item : branchItems) {
            Map<String, Object> result = categorizeAndSaveBranchMenuItem(item, restaurantId, branchId);
            results.add(result);
        }
        logger.info("Branch batch categorization complete: {}/{} items processed", 
            results.size(), branchItems.size());
        return results;
    }
}
