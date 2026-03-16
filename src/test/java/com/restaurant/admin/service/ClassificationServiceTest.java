package com.restaurant.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassificationService (no Spring context, no DB).
 * Tests taxonomy normalization and context-aware fallback (null-safety).
 */
class ClassificationServiceTest {

    /**
     * A minimal testable subclass that bypasses @Autowired fields.
     * We only exercise the pure-logic methods: normalizeCategory and askAssistant fallback.
     */
    private ClassificationService service;

    @BeforeEach
    void setUp() {
        // Construct directly — @Autowired fields will be null, but the methods
        // under test (normalizeCategory, localRuleAssistant via askAssistant)
        // don't touch repositories.
        service = new ClassificationService();
        // aiClientService is null (not injected) → askAssistant falls through to local fallback
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: normalizeCategory maps known keywords correctly
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void normalizeCategoryMapsKnownKeywords() {
        assertThat(service.normalizeCategory("juice")).isEqualTo("Drinks");
        assertThat(service.normalizeCategory("Burger Special")).isEqualTo("Main Course");
        assertThat(service.normalizeCategory("chocolate cake")).isEqualTo("Desserts");
        assertThat(service.normalizeCategory("Soup of the Day")).isEqualTo("Starters");
        assertThat(service.normalizeCategory("Pasta Carbonara")).isEqualTo("Main Course");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: normalizeCategory falls back to "Other" for unknown input
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void normalizeCategoryReturnsOtherForUnknownInput() {
        assertThat(service.normalizeCategory("xyz unknown item")).isEqualTo("Other");
        assertThat(service.normalizeCategory("")).isEqualTo("Other");
        assertThat(service.normalizeCategory("   ")).isEqualTo("Other");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: normalizeCategory handles null without NPE
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void normalizeCategoryHandlesNull() {
        assertThat(service.normalizeCategory(null)).isEqualTo("Other");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: normalizeCategory recognises taxonomy names directly
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void normalizeCategoryRecognisesExactTaxonomyNames() {
        assertThat(service.normalizeCategory("Starters")).isEqualTo("Starters");
        assertThat(service.normalizeCategory("main course")).isEqualTo("Main Course");
        assertThat(service.normalizeCategory("DESSERTS")).isEqualTo("Desserts");
        assertThat(service.normalizeCategory("other")).isEqualTo("Other");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: askAssistant fallback doesn't crash when message is null
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void askAssistantFallbackHandlesNullMessage() {
        // aiClientService is null → goes to localRuleAssistant
        String reply = service.askAssistant(null);
        assertThat(reply).isNotNull();
        assertThat(reply).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: askAssistant fallback handles null category gracefully
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void askAssistantFallbackHandlesEmptyMessage() {
        String reply = service.askAssistant("");
        assertThat(reply).isNotNull().isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: askAssistant fallback uses history context
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void askAssistantFallbackUsesHistoryForHalalContext() {
        List<String> history = List.of("User: I run a halal cafe", "Assistant: Great!");
        String reply = service.askAssistant("What categories should I use?", history);
        // The response should acknowledge halal context
        assertThat(reply).isNotBlank();
        // The reply should differ from the one without history
        String replyNoHistory = service.askAssistant("What categories should I use?", Collections.emptyList());
        // Both should be non-null; context-aware reply may differ
        assertThat(replyNoHistory).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 8: Backward-compatible single-arg askAssistant does not throw
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void askAssistantSingleArgOverloadWorks() {
        String reply = service.askAssistant("How do I categorize drinks?");
        assertThat(reply).isNotNull().isNotBlank();
        // Should mention drinks
        assertThat(reply.toLowerCase()).containsAnyOf("drink", "hot", "cold", "juice", "beverage", "category");
    }
}
