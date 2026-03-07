package com.restaurant.admin.service;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.repository.MenuItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClassificationService {

    @Autowired
    private MenuItemRepository menuItemRepository;

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

    public void applySuggestion(Long restaurantId, int suggestionIndex) {
        List<Map<String, Object>> suggestions = suggestSchemas(restaurantId);
        if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) {
            throw new IllegalArgumentException("Invalid suggestion index");
        }

        Map<String, Object> sel = suggestions.get(suggestionIndex);
        Object groupsObj = sel.get("groups");
        if (!(groupsObj instanceof Map)) return;

        java.util.Map<?,?> groups = (java.util.Map<?,?>) groupsObj;

        // lazily obtain repository beans by autowiring fields
        // Use Spring context via autowired fields if present
        try {
            // Obtain Restaurant
            com.restaurant.admin.repository.RestaurantRepository rr = null;
            try {
                rr = (com.restaurant.admin.repository.RestaurantRepository) java.lang.Class.forName("com.restaurant.admin.repository.RestaurantRepository").getDeclaredConstructor().newInstance();
            } catch (Exception ignore) {
                // ignore — we'll use menuItemRepository to find items
            }

            // For each group name, create or find Category and assign items
            for (Object gk : groups.keySet()) {
                String groupName = gk == null ? "" : gk.toString();
                // create category if missing via CategoryRepository bean
                com.restaurant.admin.model.Category cat = null;
                try {
                    // try to get CategoryRepository from Spring
                    CategoryRepository cr = org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors(
                            org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext(), com.restaurant.admin.repository.CategoryRepository.class)
                            .values().stream().findFirst().orElse(null);
                    if (cr != null) {
                        com.restaurant.admin.model.Restaurant rest = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext()
                                .getBean(com.restaurant.admin.repository.RestaurantRepository.class).findById(restaurantId).orElse(null);
                        if (rest == null) throw new RuntimeException("Restaurant not found");
                        java.util.Optional<com.restaurant.admin.model.Category> existing = cr.findByRestaurantAndNameIgnoreCase(rest, groupName);
                        if (existing.isPresent()) cat = existing.get();
                        else cat = cr.save(new com.restaurant.admin.model.Category(groupName, rest, null));
                    }
                } catch (Exception e) {
                    // If anything fails, continue without category persistence
                    cat = null;
                }

                Object listObj = groups.get(gk);
                if (!(listObj instanceof java.util.Collection)) continue;
                for (Object itemObj : (java.util.Collection<?>) listObj) {
                    String itemName = itemObj == null ? null : itemObj.toString();
                    if (itemName == null) continue;
                    // find menu item by matching name under restaurant
                    List<com.restaurant.admin.model.MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);
                    for (com.restaurant.admin.model.MenuItem mi : items) {
                        if (mi.getItemName() != null && mi.getItemName().equalsIgnoreCase(itemName)) {
                            if (cat != null) mi.setCategoryEntity(cat);
                            // save via menuItemRepository
                            try { menuItemRepository.save(mi); } catch (Exception ignore) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    }
