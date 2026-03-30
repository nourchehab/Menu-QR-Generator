package com.restaurant.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiServiceClient REST client
 * Tests communication with Python FastAPI AI service
 */
@ExtendWith(MockitoExtension.class)
public class AiServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AiServiceClient aiServiceClient;

    private String aiServiceBaseUrl = "http://localhost:8001";
    private String testItemName = "Tandoori Chicken";
    private String testDescription = "Grilled chicken marinated in yogurt and spices";
    private Double testPrice = 250.0;
    private String testRestaurantId = "1";
    private String testBranchId = "branch-a";

    @BeforeEach
    void setUp() {
        // Inject the mock RestTemplate and set the base URL
        ReflectionTestUtils.setField(aiServiceClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(aiServiceClient, "aiServiceBaseUrl", aiServiceBaseUrl);
    }

    // ================== Categorize MenuItem Tests ==================

    @Test
    void testCategorizeMenuItemSuccess() {
        // Arrange
        AiServiceClient.CategorizeResponse mockResponse = new AiServiceClient.CategorizeResponse();
        mockResponse.category = "Main Courses";
        mockResponse.confidence = 0.95;
        mockResponse.reasoning = "Grilled chicken is a main course item";
        mockResponse.alternatives = List.of("Chicken Dishes", "Grilled Items");

        String url = aiServiceBaseUrl + "/api/ai/categorize";

        when(restTemplate.postForObject(
            eq(url),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenReturn(mockResponse);

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            testItemName, testDescription, testPrice, testRestaurantId, testBranchId
        );

        // Assert
        assertNotNull(result);
        assertEquals("Main Courses", result.category);
        assertEquals(0.95, result.confidence);
        assertEquals("Grilled chicken is a main course item", result.reasoning);
        assertEquals(2, result.alternatives.size());
        verify(restTemplate, times(1)).postForObject(eq(url), any(), eq(AiServiceClient.CategorizeResponse.class));
    }

    @Test
    void testCategorizeMenuItemWithNullBranchId() {
        // Arrange
        AiServiceClient.CategorizeResponse mockResponse = new AiServiceClient.CategorizeResponse();
        mockResponse.category = "Beverages";
        mockResponse.confidence = 0.88;
        mockResponse.reasoning = "Sweet milk-based beverage";
        mockResponse.alternatives = List.of("Drinks", "Milk Items");

        String url = aiServiceBaseUrl + "/api/ai/categorize";

        when(restTemplate.postForObject(
            eq(url),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenReturn(mockResponse);

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            "Mango Lassi", "Sweet yogurt-based beverage", 50.0, "1", null
        );

        // Assert
        assertNotNull(result);
        assertEquals("Beverages", result.category);
        assertEquals(0.88, result.confidence);
        verify(restTemplate, times(1)).postForObject(eq(url), any(), eq(AiServiceClient.CategorizeResponse.class));
    }

    @Test
    void testCategorizeMenuItemServiceUnavailable() {
        // Arrange
        when(restTemplate.postForObject(
            eq(aiServiceBaseUrl + "/api/ai/categorize"),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenThrow(new RestClientException("Connection refused"));

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            testItemName, testDescription, testPrice, testRestaurantId, testBranchId
        );

        // Assert
        assertNull(result);
        verify(restTemplate, times(1)).postForObject(
            eq(aiServiceBaseUrl + "/api/ai/categorize"), any(), eq(AiServiceClient.CategorizeResponse.class));
    }

    @Test
    void testCategorizeMenuItemTimeout() {
        // Arrange
        String url = aiServiceBaseUrl + "/api/ai/categorize";
        when(restTemplate.postForObject(
            eq(url),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenThrow(new RestClientException("Read timed out"));

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            testItemName, testDescription, testPrice, testRestaurantId, testBranchId
        );

        // Assert
        assertNull(result);
    }

    @Test
    void testCategorizeMenuItemNullResponse() {
        // Arrange
        String url = aiServiceBaseUrl + "/api/ai/categorize";
        when(restTemplate.postForObject(
            eq(url),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenReturn(null);

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            testItemName, testDescription, testPrice, testRestaurantId, testBranchId
        );

        // Assert
        assertNull(result);
    }

    @Test
    void testCategorizeMenuItemWithSpecialCharacters() {
        // Arrange
        AiServiceClient.CategorizeResponse mockResponse = new AiServiceClient.CategorizeResponse();
        mockResponse.category = "Tandoori & Grilled";
        mockResponse.confidence = 0.92;
        mockResponse.reasoning = "Item with special chars";
        mockResponse.alternatives = List.of("Grilled");

        String url = aiServiceBaseUrl + "/api/ai/categorize";

        when(restTemplate.postForObject(
            eq(url),
            any(),
            eq(AiServiceClient.CategorizeResponse.class)
        )).thenReturn(mockResponse);

        // Act
        AiServiceClient.CategorizeResponse result = aiServiceClient.categorizeMenuItem(
            "Tandoori Chicken & Fish", "Mixed grilled items", 350.0, "1", "branch-a"
        );

        // Assert
        assertNotNull(result);
        assertEquals("Tandoori & Grilled", result.category);
    }

    // ================== Health Check Tests ==================

    @Test
    void testIsHealthyServiceRunning() {
        // Arrange
        String url = aiServiceBaseUrl + "/health";
        Map<String, Object> healthResponse = new HashMap<>();
        healthResponse.put("status", "alive");
        healthResponse.put("timestamp", "2026-03-29T10:00:00Z");

        when(restTemplate.getForObject(eq(url), eq(Map.class))).thenReturn(healthResponse);

        // Act
        boolean result = aiServiceClient.isHealthy();

        // Assert
        assertTrue(result);
        verify(restTemplate, times(1)).getForObject(url, Map.class);
    }

    @Test
    void testIsHealthyServiceDown() {
        // Arrange
        String url = aiServiceBaseUrl + "/health";
        when(restTemplate.getForObject(eq(url), eq(Map.class)))
            .thenThrow(new RestClientException("Connection refused"));

        // Act
        boolean result = aiServiceClient.isHealthy();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsHealthyInvalidResponse() {
        // Arrange
        String url = aiServiceBaseUrl + "/health";
        Map<String, Object> invalidResponse = new HashMap<>();
        invalidResponse.put("status", "dead");

        when(restTemplate.getForObject(eq(url), eq(Map.class))).thenReturn(invalidResponse);

        // Act
        boolean result = aiServiceClient.isHealthy();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsHealthyNullResponse() {
        // Arrange
        String url = aiServiceBaseUrl + "/health";
        when(restTemplate.getForObject(eq(url), eq(Map.class))).thenReturn(null);

        // Act
        boolean result = aiServiceClient.isHealthy();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsHealthyNetworkTimeout() {
        // Arrange
        String url = aiServiceBaseUrl + "/health";
        when(restTemplate.getForObject(eq(url), eq(Map.class)))
            .thenThrow(new RestClientException("Read timed out after 5000ms"));

        // Act
        boolean result = aiServiceClient.isHealthy();

        // Assert
        assertFalse(result);
    }

    // ================== Request/Response Object Tests ==================

    @Test
    void testCategorizeRequestConstruction() {
        // Act
        AiServiceClient.CategorizeRequest request = new AiServiceClient.CategorizeRequest(
            testItemName, testDescription, testPrice, testRestaurantId, testBranchId
        );

        // Assert
        assertEquals(testItemName, request.itemName);
        assertEquals(testDescription, request.description);
        assertEquals(testPrice, request.price);
        assertEquals(testRestaurantId, request.restaurantId);
        assertEquals(testBranchId, request.branchId);
    }

    @Test
    void testCategorizeResponseToString() {
        // Arrange
        AiServiceClient.CategorizeResponse response = new AiServiceClient.CategorizeResponse();
        response.category = "Main Courses";
        response.confidence = 0.95;
        response.reasoning = "Test reasoning";
        response.alternatives = List.of("Alt1", "Alt2");

        // Act
        String result = response.toString();

        // Assert
        assertTrue(result.contains("Main Courses"));
        assertTrue(result.contains("0.95"));
        assertTrue(result.contains("Test reasoning"));
    }

    @Test
    void testCategorizeResponseEmptyAlternatives() {
        // Arrange
        AiServiceClient.CategorizeResponse response = new AiServiceClient.CategorizeResponse();
        response.category = "Desserts";
        response.confidence = 0.99;
        response.reasoning = "High confidence dessert";
        response.alternatives = List.of();

        // Assert
        assertNotNull(response.alternatives);
        assertEquals(0, response.alternatives.size());
    }
}
