package com.restaurant.admin.controller;

import com.restaurant.admin.service.ClassificationService;
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

        if (body == null || !body.containsKey("suggestionIndex")) return ResponseEntity.badRequest().body(Map.of("error", "Missing suggestionIndex"));
        int suggestionIndex = 0;
        try {
            Object o = body.get("suggestionIndex");
            if (o instanceof Number) suggestionIndex = ((Number)o).intValue();
            else suggestionIndex = Integer.parseInt(o.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid suggestionIndex"));
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
            String reply = classificationService.askAssistant(msg.toString(), history);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Assistant error"));
        }
    }
}
