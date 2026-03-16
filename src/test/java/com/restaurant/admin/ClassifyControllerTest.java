package com.restaurant.admin;

import com.restaurant.admin.controller.ClassifyController;
import com.restaurant.admin.service.ClassificationService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClassifyController (no Spring context, no DB).
 * Covers the three MVP acceptance criteria for Jira evidence.
 */
@ExtendWith(MockitoExtension.class)
class ClassifyControllerTest {

    @Mock
    private ClassificationService classificationService;

    @Mock
    private SimpleUserService userService;

    @Mock
    private RestaurantService restaurantService;

    @InjectMocks
    private ClassifyController controller;

    private Principal principal;
    private SimpleUser user;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        principal = () -> "test@example.com";
        user = new SimpleUser();
        restaurant = new Restaurant();
        restaurant.setId(1L);
        // standard stubbings
        // mark these stubbings lenient so tests that don't exercise them remain clean
        lenient().when(userService.findByEmail(anyString())).thenReturn(user);
        lenient().when(restaurantService.getRestaurantByUser(any())).thenReturn(Optional.of(restaurant));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: /api/classify/chat returns a reply for a valid message
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void chatReturnsReplyForValidMessage() {
        when(classificationService.askAssistant(eq("Hello"), anyList()))
                .thenReturn("Hello! I can help you categorise your menu items.");

        Map<String, Object> body = Map.of("message", "Hello");
        ResponseEntity<?> response = controller.chat(body, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).containsKey("reply");
        assertThat(responseBody.get("reply").toString()).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: /api/classify/chat passes history to the service
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void chatPassesHistoryToService() {
        List<String> history = List.of("User: I run a halal cafe", "Assistant: Great!");
        when(classificationService.askAssistant(anyString(), eq(history)))
                .thenReturn("For a halal cafe, consider: Starters, Main Course, Drinks, Desserts.");

        Map<String, Object> body = Map.of(
                "message", "What categories do you suggest?",
                "history", history
        );
        ResponseEntity<?> response = controller.chat(body, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Verify the service was called WITH the history (not just the message)
        verify(classificationService).askAssistant(eq("What categories do you suggest?"), eq(history));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: /api/classify/chat returns 400 when message is missing
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void chatReturnsBadRequestWhenMessageIsMissing() {
        Map<String, Object> body = Map.of("history", List.of());
        ResponseEntity<?> response = controller.chat(body, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertThat(responseBody).containsKey("error");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: /api/classify/apply-suggestion returns error for invalid index
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void applySuggestionReturnsErrorForInvalidIndex() {
        when(userService.findByEmail("test@example.com")).thenReturn(user);
        when(restaurantService.getRestaurantByUser(user)).thenReturn(Optional.of(restaurant));

        Map<String, Object> body = Map.of("suggestionIndex", 999);
        ResponseEntity<?> response = controller.applySuggestion(body, principal);

        // Should be 4xx or 5xx — the current implementation returns 500 for thrown exceptions
        assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertThat(responseBody).containsKey("error");
    }
}
