package com.restaurant.admin.image;

/** The two processed variants generated for every uploaded menu image. */
public enum ImageVariant {

    /** Full display image (800×600, 4:3). */
    DISPLAY(ImageStandards.DISPLAY_WIDTH, ImageStandards.DISPLAY_HEIGHT,
            ImageStandards.DISPLAY_QUALITY, ImageStandards.DISPLAY_SUBDIR, false),

    /** Square thumbnail (300×300, 1:1). */
    THUMBNAIL(ImageStandards.THUMB_SIZE, ImageStandards.THUMB_SIZE,
              ImageStandards.THUMB_QUALITY, ImageStandards.THUMB_SUBDIR, true);

    public final int    width;
    public final int    height;
    public final float  quality;
    public final String subdir;
    /** When true the image is cropped to fill the target dimensions exactly. */
    public final boolean hardCrop;

    ImageVariant(int width, int height, float quality, String subdir, boolean hardCrop) {
        this.width    = width;
        this.height   = height;
        this.quality  = quality;
        this.subdir   = subdir;
        this.hardCrop = hardCrop;
    }
}
