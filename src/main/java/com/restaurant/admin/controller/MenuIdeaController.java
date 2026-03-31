package com.restaurant.admin.controller;

import com.restaurant.admin.dto.MenuIdeaRequest;
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

    /**
     * POST /api/menu-ideas/generate
     *
     * Request body (all fields optional): { "cuisineType": "Lebanese",
     * "restaurantType": "cafe", "existingCategories": "Starters, Mains,
     * Drinks", "count": 5 }
     *
     * Success: { "ideas": ["Grilled Halloumi: ...", ...] } Error: { "error":
     * "..." }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestBody(required = false) MenuIdeaRequest request,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "User not authenticated"));
        }

        if (request == null) {
            request = new MenuIdeaRequest();
        }

        int count = request.getCount() <= 0 ? 5 : request.getCount();

        try {
            List<String> ideas = menuIdeaService.generateIdeas(
                    request.getCuisineType(),
                    request.getRestaurantType(),
                    request.getExistingCategories(),
                    count
            );
            return ResponseEntity.ok(Map.of("ideas", ideas));

        } catch (RuntimeException e) {
            // Service throws RuntimeException with human-readable message
            // when AI is unavailable — surface it directly to the admin UI
            return ResponseEntity.status(503)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Unexpected error: " + e.getMessage()));
        }
    }
}
