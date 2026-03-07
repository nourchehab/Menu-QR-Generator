package com.restaurant.admin.image;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Processes uploaded menu images into standardised variants.
 *
 * <p>For every upload the pipeline:</p>
 * <ol>
 *   <li>Validates the file (type, size).</li>
 *   <li>Calls {@link AiSmartCropService} to locate the food subject.</li>
 *   <li>Generates a {@link ImageVariant#DISPLAY} variant (800×600, 4:3,
 *       JPEG 82 %) using the AI crop window when confidence is sufficient,
 *       or centre-crop as a fallback.</li>
 *   <li>Generates a {@link ImageVariant#THUMBNAIL} variant (300×300, 1:1,
 *       JPEG 78 %) — always centre-crop so it is perfectly square.</li>
 *   <li>Stores both variants under
 *       {@code <photoUploadDir>/<display|thumb>/<uuid>.jpg}.</li>
 * </ol>
 *
 * <p>Returns a {@link ProcessedImageResult} containing the stored filenames
 * (not full paths) for both variants.</p>
 */
@Service
public class ImageProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingPipeline.class);

    @Value("${file.upload.photo-dir:uploads/photos}")
    private String photoUploadDir;

    @Autowired
    private AiSmartCropService aiSmartCropService;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Validate, smart-crop, resize, compress and persist both variants.
     *
     * @param file the incoming multipart upload
     * @return filenames for the two stored variants
     * @throws IOException              on I/O failure
     * @throws IllegalArgumentException on validation failure
     */
    public ProcessedImageResult process(MultipartFile file) throws IOException {
        validate(file);

        byte[] originalBytes = file.getBytes();
        String mimeType = sanitiseMime(file.getContentType());

        // AI subject detection (non-blocking; falls back to centre-crop on failure)
        CropWindow cropWindow = aiSmartCropService.detectSubjectCrop(originalBytes, mimeType);

        BufferedImage original = readImage(originalBytes);

        // Generate variants
        String displayFilename  = processVariant(original, cropWindow, ImageVariant.DISPLAY);
        String thumbFilename    = processVariant(original, CropWindow.FULL, ImageVariant.THUMBNAIL);

        return new ProcessedImageResult(displayFilename, thumbFilename);
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }
        if (file.getSize() > ImageStandards.MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "File exceeds maximum upload size of %d MB."
                            .formatted(ImageStandards.MAX_UPLOAD_BYTES / (1024 * 1024)));
        }
        String ct = sanitiseMime(file.getContentType());
        if (!ImageStandards.ALLOWED_MIME_TYPES.contains(ct)) {
            throw new IllegalArgumentException(
                    "Unsupported image type '%s'. Allowed: JPG, PNG, WEBP.".formatted(ct));
        }
    }

    // ── Core processing ──────────────────────────────────────────────────────

    /**
     * Resize + crop + compress a single variant and write it to disk.
     *
     * @return the stored filename (basename only, e.g. "a1b2c3d4.jpg")
     */
    private String processVariant(BufferedImage original,
                                   CropWindow cropWindow,
                                   ImageVariant variant) throws IOException {

        Path dir = variantDir(variant);
        String filename = UUID.randomUUID() + ImageStandards.OUTPUT_EXTENSION;
        Path dest = dir.resolve(filename);

        BufferedImage source = applyCrop(original, cropWindow);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (variant.hardCrop) {
            // Exact fill — crop + scale to hit target dimensions exactly
            Thumbnails.of(source)
                    .size(variant.width, variant.height)
                    .crop(Positions.CENTER)
                    .outputFormat("JPEG")
                    .outputQuality(variant.quality)
                    .toOutputStream(baos);
        } else {
            // Fit — scale so the image fits within the target box (no black bars)
            Thumbnails.of(source)
                    .size(variant.width, variant.height)
                    .keepAspectRatio(true)
                    .outputFormat("JPEG")
                    .outputQuality(variant.quality)
                    .toOutputStream(baos);
        }

        Files.write(dest, baos.toByteArray());
        log.debug("Wrote {} variant → {} ({} bytes)", variant.name(), dest, baos.size());

        return variant.subdir + "/" + filename;
    }

    /**
     * Apply an AI crop window to a {@link BufferedImage}.
     * Falls back to the original image if the window is full or unreliable.
     */
    private BufferedImage applyCrop(BufferedImage img, CropWindow window) {
        if (window == CropWindow.FULL || !window.isReliable()) {
            return img;
        }

        int x = window.absX(img.getWidth());
        int y = window.absY(img.getHeight());
        int w = window.absW(img.getWidth());
        int h = window.absH(img.getHeight());

        // Guard: ensure crop fits inside the image
        w = Math.min(w, img.getWidth()  - x);
        h = Math.min(h, img.getHeight() - y);

        if (w <= 0 || h <= 0) {
            log.warn("AI crop window produced invalid dimensions; using full image.");
            return img;
        }

        return img.getSubimage(x, y, w, h);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BufferedImage readImage(byte[] bytes) throws IOException {
        try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                throw new IllegalArgumentException("Could not decode image data.");
            }
            return img;
        }
    }

    private Path variantDir(ImageVariant variant) throws IOException {
        Path dir = Paths.get(photoUploadDir, variant.subdir);
        Files.createDirectories(dir);
        return dir;
    }

    private String sanitiseMime(String contentType) {
        if (contentType == null) return "";
        return contentType.split(";")[0].trim().toLowerCase();
    }
}
