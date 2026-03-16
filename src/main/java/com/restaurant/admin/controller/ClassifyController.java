package com.restaurant.admin.controller;

import com.restaurant.admin.service.ClassificationService;
import com.restaurant.admin.service.AiClientService;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.model.SimpleUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class ClassifyController {

    @Autowired
    private ClassificationService classificationService;

    @Autowired
    private SimpleUserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired(required = false)
    private AiClientService aiClientService;

    private String resolveEmail(Principal principal) {
        if (principal == null) return null;
        return principal.getName();
    }

    @PostMapping("/api/classify/suggest-schemas")
    public ResponseEntity<?> suggestSchemas(@RequestBody(required = false) Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        String email = resolveEmail(principal);
        SimpleUser user = userService.findByEmail(email);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        Restaurant restaurant = restaurantService.getRestaurantByUser(user).orElse(null);
        if (restaurant == null) return ResponseEntity.status(400).body(Map.of("error", "Restaurant not found"));

        List<Map<String, Object>> suggestions = classificationService.suggestSchemas(restaurant.getId());
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/api/classify/apply-suggestion")
    public ResponseEntity<?> applySuggestion(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        String email = resolveEmail(principal);
        SimpleUser user = userService.findByEmail(email);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        Restaurant restaurant = restaurantService.getRestaurantByUser(user).orElse(null);
        if (restaurant == null) return ResponseEntity.status(400).body(Map.of("error", "Restaurant not found"));

        if (body == null || !(body.containsKey("suggestionIndex") || body.containsKey("index"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing suggestionIndex"));
        }
        int suggestionIndex = 0;
        try {
            Object o = body.containsKey("suggestionIndex") ? body.get("suggestionIndex") : body.get("index");
            if (o instanceof Number) suggestionIndex = ((Number)o).intValue();
            else suggestionIndex = Integer.parseInt(o.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid suggestionIndex"));
        }

        // Validate index against available suggestions to avoid 500
        List<Map<String, Object>> suggestions = classificationService.suggestSchemas(restaurant.getId());
        if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Invalid suggestion index: " + suggestionIndex));
        }

        try {
            classificationService.applySuggestion(restaurant.getId(), suggestionIndex);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/classify/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body, Principal principal) {
        Object msg = body == null ? null : body.get("message");
        if (msg == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing message"));

        // Optional conversation history to give the AI (and local fallback) context
        List<String> history = List.of();
        if (body.get("history") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> hist = (List<String>) body.get("history");
            history = hist;
        }

        try {
            // Delegate chat directly to AI client so responses vary by user message
            String userMessage = msg.toString();
            String reply;
            if (aiClientService != null) {
                reply = aiClientService.chat(userMessage);
            } else {
                // Fallback to classification service local assistant if AI client not present
                reply = classificationService.askAssistant(userMessage, history);
            }
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Assistant error"));
        }
    }
}
