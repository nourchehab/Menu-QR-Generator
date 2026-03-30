package com.restaurant.admin.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST client to communicate with the Python FastAPI AI service
 * Handles menu item categorization using Gemini + Langchain
 */
@Service
public class AiServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AiServiceClient.class);
    
    @Value("${ai.service.url:http://localhost:8001}")
    private String aiServiceBaseUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Request to categorize a menu item
     */
    public static class CategorizeRequest {
        @JsonProperty("item_name")
        public String itemName;
        
        @JsonProperty("description")
        public String description;
        
        @JsonProperty("price")
        public Double price;
        
        @JsonProperty("restaurant_id")
        public String restaurantId;
        
        @JsonProperty("branch_id")
        public String branchId;
        
        public CategorizeRequest(String itemName, String description, Double price, 
                                String restaurantId, String branchId) {
            this.itemName = itemName;
            this.description = description;
            this.price = price;
            this.restaurantId = restaurantId;
            this.branchId = branchId;
        }
    }
    
    /**
     * Response from AI categorization
     */
    public static class CategorizeResponse {
        @JsonProperty("category")
        public String category;
        
        @JsonProperty("confidence")
        public Double confidence;
        
        @JsonProperty("reasoning")
        public String reasoning;
        
        @JsonProperty("alternatives")
        public List<String> alternatives;
        
        public CategorizeResponse() {}
        
        @Override
        public String toString() {
            return "CategorizeResponse{" +
                    "category='" + category + '\'' +
                    ", confidence=" + confidence +
                    ", reasoning='" + reasoning + '\'' +
                    ", alternatives=" + alternatives +
                    '}';
        }
    }
    
    /**
     * Call the Python AI service to categorize a menu item
     * 
     * @param itemName Name of the menu item
     * @param description Description of the item
     * @param price Price in rupees/currency
     * @param restaurantId Restaurant ID from Spring Boot
     * @param branchId Branch ID
     * @return AI categorization response or null if service unavailable
     */
    public CategorizeResponse categorizeMenuItem(String itemName, String description, 
                                                 Double price, String restaurantId, 
                                                 String branchId) {
        try {
            String url = aiServiceBaseUrl + "/api/ai/categorize";
            
            CategorizeRequest request = new CategorizeRequest(
                itemName, description, price, restaurantId, branchId
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<CategorizeRequest> entity = new HttpEntity<>(request, headers);
            
            logger.info("Calling AI service: POST {} with item: {}", url, itemName);
            
            CategorizeResponse response = restTemplate.postForObject(
                url, 
                entity, 
                CategorizeResponse.class
            );
            
            logger.info("AI categorization response: {}", response);
            return response;
            
        } catch (RestClientException e) {
            logger.warn("Failed to call AI service at {}: {}", aiServiceBaseUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Error calling AI service: ", e);
            return null;
        }
    }
    
    /**
     * Check if the AI service is available
     */
    public boolean isHealthy() {
        try {
            String url = aiServiceBaseUrl + "/health";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null && "alive".equals(response.get("status"));
        } catch (Exception e) {
            logger.warn("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
