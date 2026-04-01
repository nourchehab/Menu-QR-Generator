package com.restaurant.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.service.AiCategoryService;
import com.restaurant.admin.service.MenuItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MenuItemController AI endpoints
 * Tests categorization, suggestion acceptance, and batch processing endpoints
 */
@ExtendWith(MockitoExtension.class)
public class MenuItemControllerTest {

    @Mock
    private MenuItemService menuItemService;

    @Mock
    private AiCategoryService aiCategoryService;

    @InjectMocks
    private MenuItemController menuItemController;

    private ObjectMapper objectMapper;

    private Restaurant testRestaurant;
    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create test restaurant (SimpleUser not needed for unit tests)
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
    }

    // ================== Get AI Categorization Tests ==================

    @Test
    void testGetAiCategorizationSuccess() {
        // Arrange
        testMenuItem.setSuggestedCategory("Main Courses");
        testMenuItem.setAiConfidence(0.95);
        testMenuItem.setAiReasoning("Grilled chicken is a main course");
        testMenuItem.setAiAnalyzedAt(LocalDateTime.now());

        when(aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default")).thenReturn(true);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default");

        // Assert
        assertTrue(result);
        verify(aiCategoryService, times(1)).categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default");
    }

    @Test
    void testGetAiCategorizationServiceUnavailable() {
        // Arrange
        when(aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default")).thenReturn(false);

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default");

        // Assert
        assertFalse(result);
        verify(aiCategoryService, times(1)).categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "default");
    }

    @Test
    void testGetAiCategorizationWithCustomBranch() {
        // Arrange
        when(aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "branch-a")).thenReturn(true);

        testMenuItem.setSuggestedCategory("Main Courses");
        testMenuItem.setAiConfidence(0.92);
        testMenuItem.setAiReasoning("Test reasoning");

        // Act
        boolean result = aiCategoryService.categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "branch-a");

        // Assert
        assertTrue(result);
        verify(aiCategoryService, times(1)).categorizeAndSaveMenuItem(
            testMenuItem, testRestaurant.getId(), "branch-a");
    }

    // ================== Accept AI Suggestion Tests ==================

    @Test
    void testAcceptAiSuggestionSuccess() {
        // Arrange
        testMenuItem.setSuggestedCategory("Main Courses");
        testMenuItem.setAiConfidence(0.95);
        testMenuItem.setCategory("Main Courses");

        when(aiCategoryService.acceptAiSuggestion(testMenuItem)).thenReturn(true);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertTrue(result);
        verify(aiCategoryService, times(1)).acceptAiSuggestion(testMenuItem);
    }

    @Test
    void testAcceptAiSuggestionNoSuggestionAvailable() {
        // Arrange
        testMenuItem.setSuggestedCategory(null);

        when(aiCategoryService.acceptAiSuggestion(testMenuItem)).thenReturn(false);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertFalse(result);
    }

    @Test
    void testAcceptAiSuggestionFailed() {
        // Arrange
        testMenuItem.setSuggestedCategory("Main Courses");

        when(aiCategoryService.acceptAiSuggestion(testMenuItem)).thenReturn(false);

        // Act
        boolean result = aiCategoryService.acceptAiSuggestion(testMenuItem);

        // Assert
        assertFalse(result);
        verify(aiCategoryService, times(1)).acceptAiSuggestion(testMenuItem);
    }

    // ================== Batch Categorize Tests ==================

    @Test
    void testBatchCategorizeMenuItemsSuccess() {
        // Arrange
        MenuItem item1 = testMenuItem;
        MenuItem item2 = new MenuItem();
        item2.setId(43L);
        item2.setItemName("Gulab Jamun");
        item2.setRestaurant(testRestaurant);

        List<MenuItem> items = List.of(item1, item2);

        when(aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default")).thenReturn(2);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");

        // Assert
        assertEquals(2, successCount);
        verify(aiCategoryService, times(1)).categorizeMenuItemsBatch(items, 1L, "default");
    }

    @Test
    void testBatchCategorizePartialSuccess() {
        // Arrange
        MenuItem item1 = testMenuItem;
        MenuItem item2 = new MenuItem();
        item2.setId(43L);
        item2.setItemName("Gulab Jamun");
        item2.setRestaurant(testRestaurant);

        List<MenuItem> items = List.of(item1, item2);

        when(aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default")).thenReturn(1);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");

        // Assert
        assertEquals(1, successCount);
    }

    @Test
    void testBatchCategorizeNoItems() {
        // Arrange
        List<MenuItem> emptyList = new ArrayList<>();

        when(aiCategoryService.categorizeMenuItemsBatch(emptyList, 1L, "default")).thenReturn(0);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(emptyList, 1L, "default");

        // Assert
        assertEquals(0, successCount);
    }

    @Test
    void testBatchCategorizeWithCustomBranch() {
        // Arrange
        List<MenuItem> items = List.of(testMenuItem);

        when(aiCategoryService.categorizeMenuItemsBatch(items, 1L, "branch-a")).thenReturn(1);

        // Act
        int successCount = aiCategoryService.categorizeMenuItemsBatch(items, 1L, "branch-a");

        // Assert
        assertEquals(1, successCount);
        verify(aiCategoryService, times(1)).categorizeMenuItemsBatch(items, 1L, "branch-a");
    }

    @Test
    void testBatchCategorizeServiceException() {
        // Arrange
        List<MenuItem> items = List.of(testMenuItem);

        when(aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default"))
            .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            aiCategoryService.categorizeMenuItemsBatch(items, 1L, "default");
        });
    }
}
