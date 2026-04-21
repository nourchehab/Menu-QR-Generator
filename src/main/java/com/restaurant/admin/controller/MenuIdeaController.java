package com.restaurant.admin.controller;

import com.restaurant.admin.service.MenuIdeaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu-ideas")
public class MenuIdeaController {

    @Autowired
    private MenuIdeaService menuIdeaService;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestBody(required = false) Map<String, Object> request,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "User not authenticated"));
        }

        if (request == null) {
            return badRequest("Request body is required.");
        }

        String cuisineType = normalizeText(request.get("cuisineType"));
        String restaurantType = normalizeText(request.get("restaurantType"));
        String existingCategories = normalizeText(request.get("existingCategories"));
        Integer count = parseInteger(request.get("count"));

        if (cuisineType == null) {
            return badRequest("Cuisine type is required.");
        }

        if (restaurantType == null) {
            return badRequest("Restaurant style is required.");
        }

        if (!isValidHumanText(cuisineType)) {
            return badRequest("Please enter a valid cuisine type.");
        }

        if (!isValidHumanText(restaurantType)) {
            return badRequest("Please enter a valid restaurant style.");
        }

        if (count == null) {
            return badRequest("Number of ideas is required.");
        }

        if (count < 0) {
            return badRequest("Number of ideas cannot be negative.");
        }

        if (count > 10) {
            return badRequest("Maximum number of ideas is 10.");
        }

        if (count == 0) {
            return ResponseEntity.ok(Map.of("ideas", List.of()));
        }

        try {
            List<String> ideas = menuIdeaService.generateIdeas(
                    cuisineType,
                    restaurantType,
                    existingCategories,
                    count
            );

            return ResponseEntity.ok(Map.of("ideas", ideas));

        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Unexpected error: " + e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        String text = value.toString().trim();
        if (text.isEmpty() || !text.matches("^-?\\d+$")) {
            return null;
        }

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidHumanText(String value) {
        if (value == null) {
            return false;
        }

        String cleaned = value.trim();

        if (cleaned.length() < 2 || cleaned.length() > 60) {
            return false;
        }

        if (!cleaned.matches(".*\\p{L}.*")) {
            return false;
        }

        if (cleaned.matches(".*\\d.*")) {
            return false;
        }

        return cleaned.matches("^[\\p{L}][\\p{L}\\s'&-]*[\\p{L}]$");
    }
}