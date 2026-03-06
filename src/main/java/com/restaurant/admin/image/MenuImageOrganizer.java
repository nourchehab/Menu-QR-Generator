package com.restaurant.admin.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Organises stored menu item images into category-based sub-folders.
 *
 * <p>Storage layout produced by this component:</p>
 * <pre>
 * uploads/photos/
 *   display/                 ← legacy flat files stay here
 *     starters/              ← category sub-folders
 *     mains/
 *     desserts/
 *     drinks/
 *     other/
 *   thumb/
 *     starters/
 *     mains/
 *     ...
 * </pre>
 *
 * <h3>Category mapping rules</h3>
 * The category name is normalised (lower-case, trimmed) and matched against
 * a keyword map.  Unknown categories fall into {@code other/}.
 */
@Component
public class MenuImageOrganizer {

    @Value("${file.upload.photo-dir:uploads/photos}")
    private String photoUploadDir;

    /** Canonical category names (used as folder names). */
    public enum MenuCategory {
        STARTERS, MAINS, DESSERTS, DRINKS, OTHER;

        public String folderName() { return name().toLowerCase(); }
    }

    /** Keyword → canonical category mapping. */
    private static final Map<String, MenuCategory> KEYWORD_MAP;
    static {
        Map<String, MenuCategory> m = new LinkedHashMap<>();
        // Starters / appetisers
        for (String kw : List.of("starter", "appetizer", "appetiser", "soup", "salad", "entree", "entrée"))
            m.put(kw, MenuCategory.STARTERS);
        // Mains
        for (String kw : List.of("main", "mains", "burger", "pizza", "pasta", "grill", "steak",
                "chicken", "fish", "seafood", "sandwich", "wrap", "rice", "noodle"))
            m.put(kw, MenuCategory.MAINS);
        // Desserts
        for (String kw : List.of("dessert", "cake", "ice cream", "sweet", "pudding", "brownie", "cheesecake"))
            m.put(kw, MenuCategory.DESSERTS);
        // Drinks
        for (String kw : List.of("drink", "beverage", "juice", "coffee", "tea", "smoothie",
                "cocktail", "mocktail", "soda", "water", "wine", "beer"))
            m.put(kw, MenuCategory.DRINKS);
        KEYWORD_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Resolve the canonical category for a free-text category label
     * supplied by the restaurant admin.
     *
     * @param rawCategory the category string from the menu item (may be null)
     * @return the best-matching {@link MenuCategory}
     */
    public MenuCategory resolveCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) return MenuCategory.OTHER;
        String lower = rawCategory.trim().toLowerCase();
        for (Map.Entry<String, MenuCategory> e : KEYWORD_MAP.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return MenuCategory.OTHER;
    }

    /**
     * Return the public URL path for a given variant filename and category.
     *
     * <p>Examples:
     * <pre>
     *   display/starters/abc.jpg  →  /uploads/photos/display/starters/abc.jpg
     *   thumb/mains/xyz.jpg       →  /uploads/photos/thumb/mains/xyz.jpg
     * </pre>
     * </p>
     *
     * @param variantRelativePath e.g. {@code "display/abc.jpg"} (from {@link ProcessedImageResult})
     * @param category            resolved category
     * @return public URL string ready to embed in {@code <img src="...">}
     */
    public String toOrganisedPublicUrl(String variantRelativePath, MenuCategory category) {
        if (variantRelativePath == null || variantRelativePath.isBlank()) return null;

        // variantRelativePath = "display/uuid.jpg" or "thumb/uuid.jpg"
        int slash = variantRelativePath.indexOf('/');
        if (slash < 0) {
            // plain filename — place in display/other
            return "/uploads/photos/display/" + category.folderName() + "/" + variantRelativePath;
        }

        String variantSubdir = variantRelativePath.substring(0, slash);         // "display"
        String filename      = variantRelativePath.substring(slash + 1);        // "uuid.jpg"

        return "/uploads/photos/" + variantSubdir + "/" + category.folderName() + "/" + filename;
    }

    /**
     * Physically move an already-stored variant file into the correct
     * category sub-folder.
     *
     * @param variantRelativePath relative path returned by the pipeline, e.g. {@code "display/uuid.jpg"}
     * @param category            target category
     * @return the new relative path, e.g. {@code "display/starters/uuid.jpg"}
     * @throws IOException on I/O failure
     */
    public String moveToCategory(String variantRelativePath, MenuCategory category) throws IOException {
        if (variantRelativePath == null || variantRelativePath.isBlank()) return variantRelativePath;

        int slash = variantRelativePath.indexOf('/');
        if (slash < 0) return variantRelativePath; // can't parse, leave as-is

        String variantSubdir = variantRelativePath.substring(0, slash);
        String filename      = variantRelativePath.substring(slash + 1);

        Path source = Paths.get(photoUploadDir, variantSubdir, filename);
        Path targetDir = Paths.get(photoUploadDir, variantSubdir, category.folderName());
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(filename);

        if (Files.exists(source)) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return variantSubdir + "/" + category.folderName() + "/" + filename;
    }

    /**
     * List all image filenames stored under a given category and variant.
     *
     * @param variant  e.g. {@link ImageVariant#DISPLAY}
     * @param category target category
     * @return sorted list of filenames
     */
    public List<String> listByCategory(ImageVariant variant, MenuCategory category) throws IOException {
        Path dir = Paths.get(photoUploadDir, variant.subdir, category.folderName());
        if (!Files.exists(dir)) return List.of();

        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(ImageStandards.OUTPUT_EXTENSION))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }
}
