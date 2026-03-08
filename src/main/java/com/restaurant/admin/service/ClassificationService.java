package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.restaurant.admin.repository.CategoryRepository;

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

    @Autowired(required = false)
    private AiClientService aiClientService;

    public List<Map<String, Object>> suggestSchemas(Long restaurantId) {
        List<MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);

        // Build simple suggestion variants
        Map<String, Object> s1 = new HashMap<>();
        s1.put("title", "Simple - All Items");
        s1.put("confidence", 0.70);
        Map<String, List<String>> groups1 = new HashMap<>();
        groups1.put("Menu", new ArrayList<>());
        for (MenuItem it : items) groups1.get("Menu").add(it.getItemName());
        s1.put("groups", groups1);
        s1.put("tags", Map.of());

        // Variant 2: group by first word (naive)
        Map<String, Object> s2 = new HashMap<>();
        s2.put("title", "Grouped by keyword (first word)");
        s2.put("confidence", 0.75);
        Map<String, List<String>> groups2 = new LinkedHashMap<>();
        for (MenuItem it : items) {
            String name = it.getItemName() == null ? "Other" : it.getItemName();
            String key = name.split("\\s+")[0];
            groups2.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
        }
        s2.put("groups", groups2);
        s2.put("tags", Map.of());

        // Variant 3: use existing free-text category if present, otherwise fallback to first word
        Map<String, Object> s3 = new HashMap<>();
        s3.put("title", "Use admin category when available");
        s3.put("confidence", 0.80);
        Map<String, List<String>> groups3 = new LinkedHashMap<>();
        for (MenuItem it : items) {
            String key = (it.getCategory() != null && !it.getCategory().isBlank()) ? it.getCategory() : it.getItemName().split("\\s+")[0];
            groups3.computeIfAbsent(key, k -> new ArrayList<>()).add(it.getItemName());
        }
        s3.put("groups", groups3);
        s3.put("tags", Map.of());

        return List.of(s1, s2, s3);
    }

    public boolean applySuggestion(Long restaurantId, Map<String, Object> suggestion) {
        // Backwards-compatible stub: not used. Prefer applySuggestion(restaurantId, index)
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

        // Resolve restaurant and use autowired repositories to persist categories
        com.restaurant.admin.model.Restaurant rest = restaurantRepository.findById(restaurantId).orElse(null);
        if (rest == null) throw new IllegalArgumentException("Restaurant not found");

        List<com.restaurant.admin.model.MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);

        for (Object gk : groups.keySet()) {
            String groupName = gk == null ? "" : gk.toString();
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
                        // also set the free-text category so UI reflects grouping immediately
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

    public String askAssistant(String message) {
        log.debug("askAssistant: message='{}'", message);
        if (aiClientService == null) {
            String fallback = localRuleAssistant(message);
            log.debug("askAssistant: no aiClientService, reply='{}'", fallback);
            return fallback;
        }
        Optional<String> resp = aiClientService.sendMessage(message);
        if (resp.isPresent()) {
            String out = resp.get();
            // If AI responded with a router error or Not Found, fallback to local rules
            String lower = out.toLowerCase();
            if (lower.contains("http 404") || lower.contains("not found") || lower.contains("http 410") || out.startsWith("Assistant (error):")) {
                log.warn("AI returned error response, using local fallback: {}", out);
                return localRuleAssistant(message);
            }
            log.debug("askAssistant: ai response='{}'", out);
            return out;
        } else {
            String fallback = localRuleAssistant(message);
            log.debug("askAssistant: ai empty response, reply='{}'", fallback);
            return fallback;
        }
    }

    private String localRuleAssistant(String message) {
        String m = message == null ? "" : message.trim();
        String lower = m.toLowerCase();

        if (lower.contains("mix") && (lower.contains("first") || lower.contains("second") || lower.contains("suggestion"))) {
            return "Assistant (local): Yes — you can combine suggestions. Suggested approach:\n1) Create a merged category set containing categories from both suggestions.\n2) Assign items appearing in either suggestion to the best-matching merged category.\n3) Rename or deduplicate similar category names (e.g., 'Sandwiches' vs 'Sandwich').\n4) Review and adjust item assignments manually before applying.";
        }

        if (lower.contains("i am") || lower.contains("i'm") || lower.contains("halal") || lower.contains("new york") || lower.contains("halal cafe")) {
            return "Assistant (local): Based on that context, consider adding contextual categories (e.g., 'Halal Specials', 'Local Favorites') and tags (e.g., 'Halal', 'Spicy'). You can also prioritize items by region or dietary need.";
        }

        // If the message looks like a short question, provide guidance
        if (m.endsWith("?") || lower.startsWith("how") || lower.startsWith("what") || lower.startsWith("can i")) {
            return "Assistant (local): I can help refine category suggestions — say whether you want to (a) merge suggestions, (b) create new categories, or (c) suggest tags, and paste any suggestion groups for a concrete plan.";
        }

        // Generic fallback guidance
        return "Assistant (local): I can help refine category suggestions. Tell me whether you want to merge existing suggestions, create new categories, or generate tags, and I will propose step-by-step actions.";
    }
    }
