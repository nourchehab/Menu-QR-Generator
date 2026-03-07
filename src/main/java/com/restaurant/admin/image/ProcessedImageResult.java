package com.restaurant.admin.image;

/**
 * Holds the stored filenames (relative to the photo upload directory)
 * for the two variants produced by {@link ImageProcessingPipeline}.
 *
 * <p>Examples:
 * <pre>
 *   displayFilename  = "display/a1b2c3d4-uuid.jpg"
 *   thumbFilename    = "thumb/e5f6a7b8-uuid.jpg"
 * </pre>
 * </p>
 */
public record ProcessedImageResult(
        /** Relative path for the display (800×600) variant. */
        String displayFilename,
        /** Relative path for the thumbnail (300×300) variant. */
        String thumbFilename
) {}
