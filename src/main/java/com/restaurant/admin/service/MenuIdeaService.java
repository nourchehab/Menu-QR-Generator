package com.restaurant.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MenuIdeaService {

    private static final Logger log = LoggerFactory.getLogger(MenuIdeaService.class);

    @Autowired
    private AiClientService aiClientService;

    /**
     * Generates menu item ideas via AI.
     * Throws IllegalArgumentException for invalid user input.
     * Throws RuntimeException if the AI service is unavailable or unusable.
     */
    public List<String> generateIdeas(String cuisineType,
                                      String restaurantType,
                                      String existingCategories,
                                      int count) {

        validateRequiredText(cuisineType, "Cuisine type", "Please enter a valid cuisine type.");
        validateRequiredText(restaurantType, "Restaurant style", "Please enter a valid restaurant style.");

        if (count < 0) {
            throw new IllegalArgumentException("Number of ideas cannot be negative.");
        }

        if (count > 10) {
            throw new IllegalArgumentException("Maximum number of ideas is 10.");
        }

        if (count == 0) {
            return List.of();
        }

        String cleanedCuisineType = cuisineType.trim();
        String cleanedRestaurantType = restaurantType.trim();
        String cleanedExistingCategories = normalizeOptionalText(existingCategories);

        String prompt = buildPrompt(cleanedCuisineType, cleanedRestaurantType, cleanedExistingCategories, count);
        String raw = aiClientService.chat(prompt);

        log.info("=== AI RAW RESPONSE START ===");
        log.info(raw);
        log.info("=== AI RAW RESPONSE END ===");

        if (isUnavailableResponse(raw)) {
            throw new RuntimeException(
                    "The AI service is currently unavailable. " +
                    "Please check that your Groq API key is set correctly in application.properties " +
                    "(app.ai.apiKey) and restart the app.");
        }

        List<String> ideas = parseIdeas(raw, count);

        if (ideas.isEmpty()) {
            throw new RuntimeException(
                    "AI responded but no menu ideas could be extracted. Please try again.");
        }

        return ideas;
    }

    private String buildPrompt(String cuisineType,
                               String restaurantType,
                               String existingCategories,
                               int count) {
        StringBuilder sb = new StringBuilder();

        sb.append("Generate exactly ").append(count).append(" creative menu item ideas ");
        sb.append("for a ").append(restaurantType).append(" serving ").append(cuisineType).append(" cuisine.\n");

        if (existingCategories != null) {
            sb.append("The menu already has these categories: ")
                    .append(existingCategories)
                    .append(". Suggest new items that fit well without repeating them.\n");
        }

        sb.append("\nFormat your response as a numbered list only.\n");
        sb.append("Each line must follow this format:\n");
        sb.append("NUMBER. Dish Name: One appetizing sentence description.\n\n");
        sb.append("Example:\n");
        sb.append("1. Grilled Halloumi: Golden-seared halloumi with za'atar oil and cherry tomatoes.\n");
        sb.append("2. Fattoush Salad: Crispy pita chips with fresh vegetables and tangy sumac dressing.\n\n");
        sb.append("Now write ").append(count).append(" ideas:\n");

        return sb.toString();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void validateRequiredText(String value, String fieldLabel, String invalidMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldLabel + " is required.");
        }

        String cleaned = value.trim();

        if (cleaned.length() < 2 || cleaned.length() > 60) {
            throw new IllegalArgumentException(invalidMessage);
        }

        if (!cleaned.matches(".*\\p{L}.*")) {
            throw new IllegalArgumentException(invalidMessage);
        }

        if (cleaned.matches(".*\\d.*")) {
            throw new IllegalArgumentException(invalidMessage);
        }

        if (!cleaned.matches("^[\\p{L}][\\p{L}\\s'&-]*[\\p{L}]$")) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }

    private boolean isUnavailableResponse(String raw) {
        if (raw == null || raw.isBlank()) return true;
        String lower = raw.toLowerCase();
        return lower.contains("ai is currently unavailable")
                || lower.contains("try selecting from: starters")
                || lower.contains("you asked:");
    }

    private List<String> parseIdeas(String raw, int count) {
        if (raw == null || raw.isBlank()) return List.of();

        List<String> rejectPhrases = List.of(
                "generate exactly", "format your response", "example:",
                "now write", "numbered list", "one appetizing",
                "dish name:", "number.", "here are", "here's",
                "certainly", "sure!", "of course"
        );

        String toParse = raw;
        int markerIdx = raw.lastIndexOf("Now write");
        if (markerIdx >= 0) {
            int newline = raw.indexOf('\n', markerIdx);
            if (newline >= 0) {
                toParse = raw.substring(newline + 1);
            }
        }

        List<String> result = extractNumberedLines(toParse, rejectPhrases, count);
        if (!result.isEmpty()) {
            log.info("Strategy 1 found {} ideas", result.size());
            return result;
        }

        result = extractNumberedLines(raw, rejectPhrases, count);
        if (!result.isEmpty()) {
            log.info("Strategy 2 found {} ideas", result.size());
            return result;
        }

        result = extractAnyLines(raw, rejectPhrases, count);
        log.info("Strategy 3 found {} ideas", result.size());
        return result;
    }

    private List<String> extractNumberedLines(String text,
                                              List<String> rejectPhrases,
                                              int count) {
        List<String> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.matches("^\\d+[.):].*")) continue;

            String cleaned = trimmed.replaceAll("^\\d+[.):\\s]+", "").trim();
            if (cleaned.length() < 5) continue;
            if (isReject(cleaned, rejectPhrases)) continue;

            results.add(cleaned);
            if (results.size() >= count) break;
        }
        return results;
    }

    private List<String> extractAnyLines(String text,
                                         List<String> rejectPhrases,
                                         int count) {
        List<String> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim()
                    .replaceAll("^\\d+[.):\\s]+", "")
                    .replaceAll("^[-*•·]\\s*", "")
                    .trim();

            if (trimmed.length() < 6) continue;
            if (!trimmed.matches(".*[a-zA-Z].*")) continue;
            if (isReject(trimmed, rejectPhrases)) continue;

            results.add(trimmed);
            if (results.size() >= count) break;
        }
        return results;
    }

    private boolean isReject(String text, List<String> rejectPhrases) {
        String lower = text.toLowerCase();
        return rejectPhrases.stream().anyMatch(p -> lower.contains(p.toLowerCase()));
    }
}