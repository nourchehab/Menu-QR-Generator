package com.restaurant.admin.service;

import com.restaurant.admin.image.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Facade that validates + processes menu item images via the
 * {@link ImageProcessingPipeline}, optionally organises them into
 * category sub-folders, and provides helper methods for URL conversion
 * and deletion.
 *
 * <p>All previously uploaded images (stored as a flat filename such as
 * "uuid.jpg") remain accessible through the legacy {@link #toPublicUrl}
 * method for backwards compatibility.</p>
 */
@Service
public class MenuItemImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = ImageStandards.ALLOWED_MIME_TYPES;

    @Value("${file.upload.photo-dir:uploads/photos}")
    private String photoUploadDir;

    @Autowired
    private ImageProcessingPipeline pipeline;

    @Autowired
    private MenuImageOrganizer organizer;

    // ── Validation (kept for backwards-compatibility with direct callers) ────

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        if (file.getSize() > ImageStandards.MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "File size must be less than " + (ImageStandards.MAX_UPLOAD_BYTES / (1024 * 1024)) + " MB");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_CONTENT_TYPES.contains(ct.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported image type. Allowed: JPG, PNG, WEBP");
        }
    }

    // ── Processing ───────────────────────────────────────────────────────────

    /**
     * Process and store the uploaded file, returning the display variant
     * relative path (e.g. "display/uuid.jpg").
     *
     * @deprecated Prefer {@link #storeWithVariants(MultipartFile, String)}
     *             which also returns the thumbnail path.
     */
    @Deprecated
    public String store(MultipartFile file) throws IOException {
        ProcessedImageResult result = pipeline.process(file);
        return result.displayFilename();
    }

    /**
     * Process the image, organise into the correct category sub-folder,
     * and return both variant paths.
     *
     * @param file     the upload
     * @param category free-text category label (e.g. "Starters") — may be null
     * @return both variant paths after category organisation
     */
    public ProcessedImageResult storeWithVariants(MultipartFile file, String category) throws IOException {
        ProcessedImageResult raw = pipeline.process(file);

        MenuImageOrganizer.MenuCategory cat = organizer.resolveCategory(category);

        String displayPath = organizer.moveToCategory(raw.displayFilename(), cat);
        String thumbPath   = organizer.moveToCategory(raw.thumbFilename(),   cat);

        return new ProcessedImageResult(displayPath, thumbPath);
    }

    // ── Deletion ─────────────────────────────────────────────────────────────

    /**
     * Delete an image file (any variant).
     * Accepts a relative path ("display/starters/uuid.jpg") or a legacy
     * flat filename ("uuid.jpg").
     */
    public void deleteIfExists(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return;
        try {
            Path file = storedValue.contains("/")
                    ? Paths.get(photoUploadDir, storedValue)       // new relative path
                    : Paths.get(photoUploadDir, storedValue);      // legacy flat file
            Files.deleteIfExists(file);
        } catch (IOException ignored) {}
    }

    // ── URL helpers ──────────────────────────────────────────────────────────

    /**
     * Convert a stored relative path to a public URL.
     *
     * <p>Handles three forms:</p>
     * <ul>
     *   <li>{@code "display/starters/uuid.jpg"} → {@code "/uploads/photos/display/starters/uuid.jpg"}</li>
     *   <li>{@code "uuid.jpg"} (legacy) → {@code "/uploads/photos/uuid.jpg"}</li>
     *   <li>{@code "/uploads/..."} (already a URL) → returned as-is</li>
     * </ul>
     */
    public String toPublicUrl(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) return null;
        if (storedFilename.startsWith("/uploads/")) return storedFilename;
        return "/uploads/photos/" + storedFilename;
    }

    /**
     * Convenience: convert display path to public thumb URL by substituting
     * the variant sub-directory.
     */
    public String toThumbPublicUrl(String thumbPath) {
        return toPublicUrl(thumbPath);
    }
}
