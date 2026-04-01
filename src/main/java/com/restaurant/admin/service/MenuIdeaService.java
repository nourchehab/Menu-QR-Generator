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
     * Generates menu item ideas via Groq AI (llama-3.3-70b-versatile).
     * Throws a clean RuntimeException if AI is unavailable so the
     * controller can return a proper error to the UI.
     */
    public List<String> generateIdeas(String cuisineType,
                                      String restaurantType,
                                      String existingCategories,
                                      int count) {

        String prompt = buildPrompt(cuisineType, restaurantType, existingCategories, count);
        String raw = aiClientService.chat(prompt);

        log.info("=== AI RAW RESPONSE START ===");
        log.info(raw);
        log.info("=== AI RAW RESPONSE END ===");

        // Detect AiClientService's own fallback string — means AI is down
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

    // -----------------------------------------------------------------------
    // Prompt — structured for Llama 3.3 / Groq
    // -----------------------------------------------------------------------

    private String buildPrompt(String cuisineType, String restaurantType,
                                String existingCategories, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate exactly ").append(count).append(" creative menu item ideas");

        if (cuisineType != null && !cuisineType.isBlank()) {
            sb.append(" for a ").append(cuisineType.trim());
        }
        if (restaurantType != null && !restaurantType.isBlank()) {
            sb.append(" ").append(restaurantType.trim());
        }
        sb.append(".\n");

        if (existingCategories != null && !existingCategories.isBlank()) {
            sb.append("The menu already has: ").append(existingCategories.trim()).append(". ");
            sb.append("Suggest complementary new items.\n");
        }

        sb.append("\nFormat your response as a numbered list ONLY. Each line:\n");
        sb.append("NUMBER. Dish Name: One appetizing sentence description.\n\n");
        sb.append("Example:\n");
        sb.append("1. Grilled Halloumi: Golden-seared halloumi with za'atar oil and cherry tomatoes.\n");
        sb.append("2. Fattoush Salad: Crispy pita chips with fresh vegetables and tangy sumac dressing.\n\n");
        sb.append("Now write ").append(count).append(" ideas:\n");

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Detect AiClientService's own fallback message
    // -----------------------------------------------------------------------

    private boolean isUnavailableResponse(String raw) {
        if (raw == null || raw.isBlank()) return true;
        String lower = raw.toLowerCase();
        return lower.contains("ai is currently unavailable")
                || lower.contains("try selecting from: starters")
                || lower.contains("you asked:");
    }

    // -----------------------------------------------------------------------
    // Parser — Groq/Llama reliably returns numbered lists so Strategy 1
    // will almost always succeed. Strategies 2 & 3 are safety nets.
    // -----------------------------------------------------------------------

    private List<String> parseIdeas(String raw, int count) {
        if (raw == null || raw.isBlank()) return List.of();

        // Lines to reject — prompt echoes or meta-commentary
        List<String> rejectPhrases = List.of(
                "generate exactly", "format your response", "example:",
                "now write", "numbered list", "one appetizing",
                "dish name:", "number.", "here are", "here's",
                "certainly", "sure!", "of course"
        );

        // Strategy 1: look for "Now write N ideas:" marker, parse after it
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

        // Strategy 2: scan full response for numbered lines
        result = extractNumberedLines(raw, rejectPhrases, count);
        if (!result.isEmpty()) {
            log.info("Strategy 2 found {} ideas", result.size());
            return result;
        }

        // Strategy 3: accept any reasonable-looking line
        result = extractAnyLines(raw, rejectPhrases, count);
        log.info("Strategy 3 found {} ideas", result.size());
        return result;
    }

    /** Accept lines starting with digit: "1. Name: desc" */
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

    /** Fallback: any non-prompt line with real content */
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