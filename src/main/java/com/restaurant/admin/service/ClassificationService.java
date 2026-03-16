package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassificationService {

    private final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;

    @Value("${app.ai.apiKey:}")
    private String aiApiKey;

    @Value("${app.ai.confidenceThreshold:0.6}")
    private double confidenceThreshold;

    @Autowired(required = false)
    private AiClientService aiClientService;

    // Allowed taxonomy values
    private static final List<String> TAXONOMY = List.of(
            "Starters", "Main Course", "Drinks", "Desserts", "Other"
    );

    // Keyword → taxonomy mapping for normalization
    private static final Map<String, String> KEYWORD_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Starters
        m.put("starter", "Starters");
        m.put("appetizer", "Starters");
        m.put("soup", "Starters");
        m.put("salad", "Starters");
        m.put("entrée", "Starters");
        m.put("entree", "Starters");
        // Main Course
        m.put("main", "Main Course");
        m.put("burger", "Main Course");
        m.put("sandwich", "Main Course");
        m.put("pasta", "Main Course");
        m.put("pizza", "Main Course");
        m.put("steak", "Main Course");
        m.put("chicken", "Main Course");
        m.put("rice", "Main Course");
        m.put("wrap", "Main Course");
        m.put("shawarma", "Main Course");
        m.put("kebab", "Main Course");
        m.put("grill", "Main Course");
        // Drinks
        m.put("drink", "Drinks");
        m.put("juice", "Drinks");
        m.put("water", "Drinks");
        m.put("coffee", "Drinks");
        m.put("tea", "Drinks");
        m.put("soda", "Drinks");
        m.put("shake", "Drinks");
        m.put("smoothie", "Drinks");
        m.put("lemonade", "Drinks");
        m.put("beverage", "Drinks");
        // Desserts
        m.put("dessert", "Desserts");
        m.put("cake", "Desserts");
        m.put("ice cream", "Desserts");
        m.put("icecream", "Desserts");
        m.put("cookie", "Desserts");
        m.put("brownie", "Desserts");
        m.put("pastry", "Desserts");
        m.put("sweet", "Desserts");
        m.put("chocolate", "Desserts");
        KEYWORD_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Normalizes a raw category string (from AI or heuristic) to one of the
     * allowed taxonomy values. Falls back to "Other" if no match found.
     */
    public String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Other";
        String lower = raw.trim().toLowerCase();
        for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        // Check if the raw value is already one of the taxonomy values (case-insensitive)
        for (String taxon : TAXONOMY) {
            if (taxon.equalsIgnoreCase(lower)) return taxon;
        }
        return "Other";
    }

    public List<Map<String, Object>> suggestSchemas(Long restaurantId) {
        List<MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);

        // Variant 1: All in one "Menu" group
        Map<String, Object> s1 = new HashMap<>();
        s1.put("title", "Simple - All Items");
        s1.put("confidence", 0.70);
        Map<String, List<String>> groups1 = new HashMap<>();
        groups1.put("Menu", new ArrayList<>());
        for (MenuItem it : items) groups1.get("Menu").add(it.getItemName());
        s1.put("groups", groups1);
        s1.put("tags", Map.of());

        // Variant 2: Group by first word (null-safe)
        Map<String, Object> s2 = new HashMap<>();
        s2.put("title", "Grouped by keyword (first word)");
        s2.put("confidence", 0.75);
        Map<String, List<String>> groups2 = new LinkedHashMap<>();
        for (MenuItem it : items) {
            String name = (it.getItemName() == null || it.getItemName().isBlank()) ? "Other" : it.getItemName().trim();
            String[] parts = name.split("\\s+");
            String key = normalizeCategory(parts[0]);
            groups2.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
        }
        s2.put("groups", groups2);
        s2.put("tags", Map.of());

        // Variant 3: Use existing free-text category if present, otherwise normalized first word
        Map<String, Object> s3 = new HashMap<>();
        s3.put("title", "Use admin category when available");
        s3.put("confidence", 0.80);
        Map<String, List<String>> groups3 = new LinkedHashMap<>();
        for (MenuItem it : items) {
            String name = (it.getItemName() == null || it.getItemName().isBlank()) ? "Other" : it.getItemName().trim();
            String rawCategory = (it.getCategory() != null && !it.getCategory().isBlank())
                    ? it.getCategory()
                    : name.split("\\s+")[0];
            String key = normalizeCategory(rawCategory);
            groups3.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
        }
        s3.put("groups", groups3);
        s3.put("tags", Map.of());

        return List.of(s1, s2, s3);
    }

    public boolean applySuggestion(Long restaurantId, Map<String, Object> suggestion) {
        // Backwards-compatible stub
        return true;
    }

    @Transactional
    public void applySuggestion(Long restaurantId, int suggestionIndex) {
        List<Map<String, Object>> suggestions = suggestSchemas(restaurantId);
        if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) {
            throw new IllegalArgumentException("Invalid suggestion index");
        }

        Map<String, Object> sel = suggestions.get(suggestionIndex);
        Object groupsObj = sel.get("groups");
        if (!(groupsObj instanceof Map)) return;

        java.util.Map<?,?> groups = (java.util.Map<?,?>) groupsObj;

        com.restaurant.admin.model.Restaurant rest = restaurantRepository.findById(restaurantId).orElse(null);
        if (rest == null) throw new IllegalArgumentException("Restaurant not found");

        List<com.restaurant.admin.model.MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);

        for (Object gk : groups.keySet()) {
            String groupName = gk == null ? "Other" : gk.toString();
            com.restaurant.admin.model.Category cat = categoryRepository.findByRestaurantAndNameIgnoreCase(rest, groupName)
                    .orElseGet(() -> categoryRepository.save(new com.restaurant.admin.model.Category(groupName, rest, null)));

            Object listObj = groups.get(gk);
            if (!(listObj instanceof java.util.Collection)) continue;
            for (Object itemObj : (java.util.Collection<?>) listObj) {
                String itemName = itemObj == null ? null : itemObj.toString();
                if (itemName == null) continue;
                for (com.restaurant.admin.model.MenuItem mi : items) {
                    if (mi.getItemName() != null && mi.getItemName().equalsIgnoreCase(itemName)) {
                        mi.setCategoryEntity(cat);
                        try {
                            mi.setCategory(cat.getName());
                            menuItemRepository.save(mi);
                        } catch (Exception ignore) {}
                    }
                }
            }
        }
    }

    public boolean isAiKeyConfigured() {
        return aiApiKey != null && !aiApiKey.isBlank();
    }

    /**
     * Ask the assistant, optionally passing prior conversation history
     * to avoid returning the same response every time.
     */
    public String askAssistant(String message, List<String> history) {
        log.debug("askAssistant: message='{}' historySize={}", message, history == null ? 0 : history.size());
        if (aiClientService == null) {
            return localRuleAssistant(message, history);
        }
        Optional<String> resp = aiClientService.sendMessage(message, history);
        if (resp.isPresent()) {
            String out = resp.get();
            String lower = out.toLowerCase();
            if (lower.contains("http 404") || lower.contains("not found")
                    || lower.contains("http 410") || out.startsWith("Assistant (error):")) {
                log.warn("AI returned error response, using local fallback: {}", out);
                return localRuleAssistant(message, history);
            }
            log.debug("askAssistant: ai response='{}'", out);
            return out;
        }
        return localRuleAssistant(message, history);
    }

    /** Backward-compatible single-arg overload. */
    public String askAssistant(String message) {
        return askAssistant(message, Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Context-aware local fallback (no AI available)
    // -------------------------------------------------------------------------
    private String localRuleAssistant(String message, List<String> history) {
        String m = message == null ? "" : message.trim();
        String lower = m.toLowerCase();

        // Build context from history for smarter responses
        String historyText = (history == null || history.isEmpty())
                ? ""
                : String.join(" ", history).toLowerCase();
        boolean hasHalalContext = historyText.contains("halal") || lower.contains("halal");
        boolean hasCafeContext = historyText.contains("cafe") || historyText.contains("coffee shop") || lower.contains("cafe");


        // Combining suggestions
        if (lower.contains("mix") && (lower.contains("first") || lower.contains("second") || lower.contains("suggestion"))) {
            return "Yes — you can combine suggestions:\n" +
                   "1. Merge the category lists from both suggestions.\n" +
                   "2. Assign items to the best-matching merged category.\n" +
                   "3. Rename duplicates (e.g. 'Sandwiches' vs 'Sandwich' → pick one).\n" +
                   "4. Review manually before applying.";
        }

        // Drinks / beverages question
        if (lower.contains("drink") || lower.contains("beverage") || lower.contains("juice") || lower.contains("coffee")) {
            String prefix = hasCafeContext ? "For a cafe, " : hasHalalContext ? "For a halal establishment, " : "";
            return prefix + "I'd suggest a **Drinks** category with sub-groups: 'Hot Drinks', 'Cold Drinks', and 'Fresh Juices'. " +
                   "This keeps your beverage menu scannable across all customer segments.";
        }

        // Starters question
        if (lower.contains("starter") || lower.contains("appetizer") || lower.contains("soup") || lower.contains("salad")) {
            return "Consider a **Starters** category covering soups, salads, and small plates. " +
                   "These are typically low-cost, high-margin items — grouping them helps upselling.";
        }

        // Desserts question
        if (lower.contains("dessert") || lower.contains("cake") || lower.contains("sweet") || lower.contains("ice cream")) {
            return "A **Desserts** category works best placed at the bottom of the menu. " +
                   "Include cakes, pastries, and ice cream variants as sub-items.";
        }

        // Context intro (halal / cafe / restaurant)
        if (lower.contains("i am") || lower.contains("i'm") || lower.contains("we are") || lower.contains("i run")
                || lower.contains("halal") || lower.contains("new york") || lower.contains("halal cafe")) {
            String suggestion = hasHalalContext
                    ? "Consider categories like **Halal Specials**, **Main Course**, **Starters**, **Drinks**, and **Desserts**. " +
                      "Add tags such as 'Halal Certified' or 'Spicy' for filtering."
                    : hasCafeContext
                    ? "For a cafe, typical categories are **Breakfast**, **Sandwiches**, **Hot Drinks**, **Cold Drinks**, and **Desserts**."
                    : "Consider using the standard taxonomy: **Starters**, **Main Course**, **Drinks**, **Desserts**.";
            return "Thanks for the context! " + suggestion;
        }

        // Taxonomy / category question
        if (lower.contains("categor") || lower.contains("taxonomy") || lower.contains("group")) {
            return "The built-in taxonomy has 5 categories: **Starters**, **Main Course**, **Drinks**, **Desserts**, **Other**. " +
                   "You can pick any suggestion from the list above and I'll apply it, or ask me to customize further.";
        }

        // General question
        if (m.endsWith("?") || lower.startsWith("how") || lower.startsWith("what") || lower.startsWith("can i")
                || lower.startsWith("which") || lower.startsWith("should")) {
            return "I can help you refine category suggestions. Tell me:\n" +
                   "- (a) Do you want to **merge** suggestions?\n" +
                   "- (b) Do you want to **create new** custom categories?\n" +
                   "- (c) Do you want me to suggest **tags** (e.g. Spicy, Vegan, Halal)?\n" +
                   "Just describe your menu or restaurant type and I'll give a concrete plan.";
        }

        // Generic fallback
        String contextHint = hasHalalContext ? " (Based on your halal context)" : hasCafeContext ? " (Based on your cafe context)" : "";
        return "I can help refine your category suggestions" + contextHint + ". " +
               "Say whether you want to merge suggestions, create new categories, or generate tags, and I'll propose step-by-step actions.";
    }
}
