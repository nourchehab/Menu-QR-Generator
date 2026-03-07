package com.restaurant.admin.image;

/**
 * Represents a region of interest inside an image, expressed as
 * fractional coordinates (0.0–1.0) relative to the original dimensions.
 *
 * <p>Produced by {@link AiSmartCropService} and consumed by
 * {@link ImageProcessingPipeline}.</p>
 */
public record CropWindow(
        /** Left edge, fraction of image width. */
        double x,
        /** Top edge, fraction of image height. */
        double y,
        /** Width of the crop region, fraction of image width. */
        double width,
        /** Height of the crop region, fraction of image height. */
        double height,
        /** Model confidence 0–1. */
        double confidence
) {
    /** A full-image window used as a no-op / fallback. */
    public static final CropWindow FULL = new CropWindow(0, 0, 1, 1, 1.0);

    /** Returns true if the model was confident enough to use this crop. */
    public boolean isReliable() {
        return confidence >= ImageStandards.AI_CROP_CONFIDENCE_THRESHOLD;
    }

    /** Convert to absolute pixel coordinates given the original image dimensions. */
    public int absX(int imgWidth)      { return clamp((int) (x      * imgWidth),  0, imgWidth  - 1); }
    public int absY(int imgHeight)     { return clamp((int) (y      * imgHeight), 0, imgHeight - 1); }
    public int absW(int imgWidth)      { return clamp((int) (width  * imgWidth),  1, imgWidth);      }
    public int absH(int imgHeight)     { return clamp((int) (height * imgHeight), 1, imgHeight);     }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
