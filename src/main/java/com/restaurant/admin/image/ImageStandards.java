package com.restaurant.admin.image;

/**
 * Single source of truth for all menu image standards.
 *
 * <h3>Dimension rationale</h3>
 * <ul>
 *   <li>DISPLAY variant (800×600, 4:3) – used on the public menu page; wide
 *       enough for high-DPI screens, keeps file size reasonable.</li>
 *   <li>THUMBNAIL variant (300×300, 1:1) – used in the admin manage-items
 *       list and on menu cards; square crop looks consistent in a grid.</li>
 *   <li>Original is never served directly; we always serve a processed
 *       variant.</li>
 * </ul>
 *
 * <h3>Compression</h3>
 * JPEG quality 0.82 gives a good visual result while cutting typical
 * file sizes to ≈ 40 % of the raw upload.
 */
public final class ImageStandards {

    private ImageStandards() {}

    // ── Accepted uploads ─────────────────────────────────────────────────────

    /** Maximum accepted upload size before any processing (bytes). */
    public static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024; // 10 MB

    /** Accepted MIME types. */
    public static final java.util.Set<String> ALLOWED_MIME_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp");

    // ── Display variant ───────────────────────────────────────────────────────

    /** Width in pixels for the display (full-menu) variant. */
    public static final int DISPLAY_WIDTH  = 800;

    /** Height in pixels for the display (full-menu) variant. */
    public static final int DISPLAY_HEIGHT = 600;

    /** Aspect ratio of the display variant (4:3). */
    public static final double DISPLAY_ASPECT = (double) DISPLAY_WIDTH / DISPLAY_HEIGHT;

    /** JPEG quality for the display variant (0–1). */
    public static final float DISPLAY_QUALITY = 0.82f;

    /** Sub-directory suffix appended to the photo upload dir. */
    public static final String DISPLAY_SUBDIR = "display";

    // ── Thumbnail variant ─────────────────────────────────────────────────────

    /** Width × height for the thumbnail (square) variant. */
    public static final int THUMB_SIZE = 300;

    /** JPEG quality for the thumbnail variant. */
    public static final float THUMB_QUALITY = 0.78f;

    /** Sub-directory suffix appended to the photo upload dir. */
    public static final String THUMB_SUBDIR = "thumb";

    // ── Output format ─────────────────────────────────────────────────────────

    /**
     * All processed images are stored as JPEG regardless of the original
     * format, so we always serve a known type with predictable file sizes.
     */
    public static final String OUTPUT_EXTENSION = ".jpg";
    public static final String OUTPUT_MIME_TYPE  = "image/jpeg";

    // ── AI smart-crop ─────────────────────────────────────────────────────────

    /**
     * Minimum confidence returned by the AI subject-detection before we
     * trust the crop window.  Values below this threshold fall back to
     * centre-crop.
     */
    public static final double AI_CROP_CONFIDENCE_THRESHOLD = 0.60;
}
