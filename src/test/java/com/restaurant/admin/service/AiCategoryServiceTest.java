package com.restaurant.admin.service;

import com.restaurant.admin.model.Category;
import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.CategoryRepository;
import com.restaurant.admin.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiCategoryService
 * Tests AI categorization, batch processing, and category auto-creation
 */
@ExtendWith(MockitoExtension.class)
public class AiCategoryServiceTest {

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private AiCategoryService aiCategoryService;

    private Restaurant testRestaurant;
    private MenuItem testMenuItem;
    private Category testCategory;
    private AiServiceClient.CategorizeResponse mockAiResponse;

    @BeforeEach
    void setUp() {
        // Create test restaurant
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setRestaurantName("Test Restaurant");
        testRestaurant.setRestaurantType("finedining");

        // Create test menu item
        testMenuItem = new MenuItem();
        testMenuItem.setId(42L);
        testMenuItem.setItemName("Tandoori Chicken");
        testMenuItem.setItemDescription("Grilled chicken marinated in yogurt and spices");
        testMenuItem.setItemPrice(new BigDecimal("250.00"));
        testMenuItem.setRestaurant(testRestaurant);
        testMenuItem.setCreatedAt(LocalDateTime.now());

        // Create test category
        testCategory = new Category();
        testCategory.setId(8L);
        testCategory.setName("Main Courses");
        testCategory.setRestaurant(testRestaurant);
        testCategory.setCreatedAt(LocalDateTime.now());

        // Create mock AI response
        mockAiResponse = new AiServiceClient.CategorizeResponse();
        mockAiResponse.category = "Main Courses";
        mockAiResponse.confidence = 0.95;
        mockAiResponse.reasoning = "Grilled chicken marinated in yogurt is a main course";
        mockAiResponse.alternatives = List.of("Chicken Dishes", "Tandoori Items");
    }

    // ================== categorizeAndSaveMenuItem Tests ==================

    @Test
    void testCategorizeAndSaveMenuItemSuccess() {
        // Arrange
        when(aiServiceClient.categorizeMenuItem(
            "Tandoori Chicken", 
            "Grilled chicken marinated in yogurt and spices", 
            250.0, 
            "1", 
            "default"
        )).thenReturn(mockAiResponse);

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "default");

        // Assert
        assertTrue(result);
        assertEquals("Main Courses", testMenuItem.getSuggestedCategory());
        assertEquals(0.95, testMenuItem.getAiConfidence());
        assertEquals("Grilled chicken marinated in yogurt is a main course", testMenuItem.getAiReasoning());
        assertNotNull(testMenuItem.getAiAnalyzedAt());
        verify(menuItemRepository, times(1)).save(testMenuItem);
        verify(aiServiceClient, times(1)).categorizeMenuItem(any(), any(), any(), any(), any());
    }

    @Test
    void testCategorizeAndSaveMenuItemWithBranchId() {
        // Arrange
        when(aiServiceClient.categorizeMenuItem(
            anyString(), anyString(), any(Double.class), 
            eq("1"), eq("branch-a")
        )).thenReturn(mockAiResponse);

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "branch-a");

        // Assert
        assertTrue(result);
        verify(aiServiceClient, times(1)).categorizeMenuItem(
            anyString(), anyString(), any(Double.class), eq("1"), eq("branch-a")
        );
    }

    @Test
    void testCategorizeAndSaveMenuItemServiceUnavailable() {
        // Arrange
        when(aiServiceClient.categorizeMenuItem(
            anyString(), anyString(), any(Double.class), 
            anyString(), anyString()
        )).thenReturn(null);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "default");

        // Assert
        assertFalse(result);
        verify(menuItemRepository, never()).save(testMenuItem);
    }

    @Test
    void testCategorizeAndSaveMenuItemException() {
        // Arrange
        when(aiServiceClient.categorizeMenuItem(
            anyString(), anyString(), any(Double.class), 
            anyString(), anyString()
        )).thenThrow(new RuntimeException("Unexpected error"));

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "default");

        // Assert
        assertFalse(result);
        verify(menuItemRepository, never()).save(testMenuItem);
    }

    @Test
    void testCategorizeAndSaveMenuItemWithHighConfidence() {
        // Arrange
        mockAiResponse.confidence = 0.99;
        when(aiServiceClient.categorizeMenuItem(
            anyString(), anyString(), any(Double.class), 
            anyString(), anyString()
        )).thenReturn(mockAiResponse);

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "default");

        // Assert
        assertTrue(result);
        assertEquals(0.99, testMenuItem.getAiConfidence());
    }

    @Test
    void testCategorizeAndSaveMenuItemWithLowConfidence() {
        // Arrange
        mockAiResponse.confidence = 0.45;
        when(aiServiceClient.categorizeMenuItem(
            anyString(), anyString(), any(Double.class), 
            anyString(), anyString()
        )).thenReturn(mockAiResponse);

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(testMenuItem, 1L, "default");

        // Assert
        assertTrue(result);
        assertEquals(0.45, testMenuItem.getAiConfidence());
    }

    // ================== categorizeMenuItemsBatch Tests ==================

    @Test
    void testCategorizeMenuItemsBatchSuccess() {
        // Arrange
        MenuItem item1 = testMenuItem;
        MenuItem item2 = new MenuItem();
        item2.setId(43L);
        item2.setItemName("Gulab Jamun");
        item2.setItemDescription("Sweet fried milk-solid balls in sugar syrup");
        item2.setItemPrice(new BigDecimal("80.00"));
        item2.setRestaurant(testRestaurant);

        List<MenuItem> items = List.of(item1, item2);

        when(aiServiceClient.categorizeMenuItem(anyString(), anyString(), any(Double.class), anyString(), anyString()))
            .thenReturn(mockAiResponse);

        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");

        // Assert
        assertEquals(2, successCount);
        verify(aiServiceClient, times(2)).categorizeMenuItem(anyString(), anyString(), any(Double.class), anyString(), anyString());
        verify(menuItemRepository, times(2)).save(any(MenuItem.class));
    }

    @Test
    void testCategorizeMenuItemsBatchPartialSuccess() {
        // Arrange
        MenuItem item1 = testMenuItem;
        MenuItem item2 = new MenuItem();
        item2.setId(43L);
        item2.setItemName("Failed Item");
        item2.setItemDescription("This categorization will fail");
        item2.setItemPrice(new BigDecimal("100.00"));
        item2.setRestaurant(testRestaurant);

        List<MenuItem> items = List.of(item1, item2);

        // First call succeeds, second fails
        when(aiServiceClient.categorizeMenuItem(anyString(), anyString(), any(Double.class), anyString(), anyString()))
            .thenReturn(mockAiResponse)
            .thenReturn(null);

        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");

        // Assert
        assertEquals(1, successCount);
    }

    @Test
    void testCategorizeMenuItemsBatchEmpty() {
        // Arrange
        List<MenuItem> emptyList = new ArrayList<>();

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(emptyList, 1L, "default");

        // Assert
        assertEquals(0, successCount);
        verify(aiServiceClient, never()).categorizeMenuItem(anyString(), anyString(), any(Double.class), anyString(), anyString());
    }

    @Test
    void testCategorizeMenuItemsBatchAllFail() {
        // Arrange
        MenuItem item1 = testMenuItem;
        MenuItem item2 = new MenuItem();
        item2.setId(43L);
        item2.setItemName("Another Item");
        item2.setRestaurant(testRestaurant);

        List<MenuItem> items = List.of(item1, item2);

        when(aiServiceClient.categorizeMenuItem(anyString(), anyString(), any(Double.class), anyString(), anyString()))
            .thenReturn(null);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");

        // Assert
        assertEquals(0, successCount);
        verify(menuItemRepository, never()).save(any(MenuItem.class));
    }

    // ================== acceptAiSuggestion Tests ==================

    @Test
    void testAcceptAiSuggestionSuccess() {
        // Arrange
        testMenuItem.setSuggestedCategory("Main Courses");
        testMenuItem.setAiConfidence(0.95);

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "Main Courses"))
            .thenReturn(Optional.of(testCategory));

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertTrue(result);
        assertEquals("Main Courses", testMenuItem.getCategory());
        assertEquals(testCategory, testMenuItem.getCategoryEntity());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testAcceptAiSuggestionAutoCreatesCategory() {
        // Arrange
        testMenuItem.setSuggestedCategory("New Category");
        testMenuItem.setAiConfidence(0.85);

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "New Category"))
            .thenReturn(Optional.empty());

        Category newCategory = new Category();
        newCategory.setId(9L);
        newCategory.setName("New Category");
        newCategory.setRestaurant(testRestaurant);

        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);
        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertTrue(result);
        assertEquals("New Category", testMenuItem.getCategory());
        verify(categoryRepository, times(1)).save(any(Category.class));
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testAcceptAiSuggestionNoSuggestion() {
        // Arrange
        testMenuItem.setSuggestedCategory(null);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertFalse(result);
        verify(menuItemRepository, never()).save(testMenuItem);
    }

    @Test
    void testAcceptAiSuggestionException() {
        // Arrange
        testMenuItem.setSuggestedCategory("Main Courses");

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "Main Courses"))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertFalse(result);
    }

    @Test
    void testAcceptAiSuggestionCaseInsensitive() {
        // Arrange
        testMenuItem.setSuggestedCategory("main courses");

        Category existingCategory = new Category();
        existingCategory.setId(8L);
        existingCategory.setName("Main Courses");

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "main courses"))
            .thenReturn(Optional.of(existingCategory));

        when(menuItemRepository.save(testMenuItem)).thenReturn(testMenuItem);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertTrue(result);
        assertEquals(existingCategory, testMenuItem.getCategoryEntity());
    }

    // ================== findOrCreateCategory Tests ==================

    @Test
    void testFindOrCreateCategoryExisting() {
        // Arrange
        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "Main Courses"))
            .thenReturn(Optional.of(testCategory));

        // Act
        Category result = aiCategoryService.findOrCreateCategory(testRestaurant, "Main Courses");

        // Assert
        assertNotNull(result);
        assertEquals(testCategory.getId(), result.getId());
        assertEquals("Main Courses", result.getName());
        verify(categoryRepository, times(1)).findByRestaurantAndNameIgnoreCase(testRestaurant, "Main Courses");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void testFindOrCreateCategoryNew() {
        // Arrange
        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "Appetizers"))
            .thenReturn(Optional.empty());

        Category newCategory = new Category();
        newCategory.setId(10L);
        newCategory.setName("Appetizers");
        newCategory.setRestaurant(testRestaurant);
        newCategory.setCreatedAt(LocalDateTime.now());

        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);

        // Act
        Category result = aiCategoryService.findOrCreateCategory(testRestaurant, "Appetizers");

        // Assert
        assertNotNull(result);
        assertEquals("Appetizers", result.getName());
        assertEquals(testRestaurant, result.getRestaurant());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void testFindOrCreateCategoryMultipleRestaurants() {
        // Arrange
        Restaurant restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        restaurant2.setRestaurantName("Another Restaurant");

        Category category1 = new Category();
        category1.setId(8L);
        category1.setName("Main Courses");
        category1.setRestaurant(testRestaurant);

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(testRestaurant, "Main Courses"))
            .thenReturn(Optional.of(category1));

        when(categoryRepository.findByRestaurantAndNameIgnoreCase(restaurant2, "Main Courses"))
            .thenReturn(Optional.empty());

        Category newCategory = new Category();
        newCategory.setId(11L);
        newCategory.setName("Main Courses");
        newCategory.setRestaurant(restaurant2);

        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);

        // Act
        Category result1 = aiCategoryService.findOrCreateCategory(testRestaurant, "Main Courses");
        Category result2 = aiCategoryService.findOrCreateCategory(restaurant2, "Main Courses");

        // Assert
        assertEquals(testRestaurant.getId(), result1.getRestaurant().getId());
        assertEquals(restaurant2.getId(), result2.getRestaurant().getId());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    // ================== isAiServiceAvailable Tests ==================

    @Test
    void testIsAiServiceAvailable() {
        // Arrange
        when(aiServiceClient.isHealthy()).thenReturn(true);

        // Act
        boolean result = aiCategoryService.isAiServiceAvailable();

        // Assert
        assertTrue(result);
        verify(aiServiceClient, times(1)).isHealthy();
    }

    @Test
    void testIsAiServiceUnavailable() {
        // Arrange
        when(aiServiceClient.isHealthy()).thenReturn(false);

        // Act
        boolean result = aiCategoryService.isAiServiceAvailable();

        // Assert
        assertFalse(result);
        verify(aiServiceClient, times(1)).isHealthy();
    }
}
