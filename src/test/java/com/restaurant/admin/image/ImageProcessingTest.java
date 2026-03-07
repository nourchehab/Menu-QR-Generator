package com.restaurant.admin.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests covering:
 * <ul>
 *   <li>ImageStandards constants sanity checks</li>
 *   <li>CropWindow helper methods</li>
 *   <li>MenuImageOrganizer category resolution + URL building</li>
 *   <li>ImageProcessingPipeline resize/crop/compress (no AI, no network)</li>
 *   <li>Visual regression: output dimensions match the declared standards</li>
 *   <li>Performance: pipeline completes within time budget</li>
 * </ul>
 */
class ImageProcessingTest {

    @TempDir
    Path tempDir;

    private ImageProcessingPipeline pipeline;
    private MenuImageOrganizer organizer;

    @BeforeEach
    void setUp() {
        // AiSmartCropService with no API key → always returns CropWindow.FULL (no network)
        AiSmartCropService ai = new AiSmartCropService();
        ReflectionTestUtils.setField(ai, "apiKey", "");

        pipeline = new ImageProcessingPipeline();
        ReflectionTestUtils.setField(pipeline, "photoUploadDir", tempDir.toString());
        ReflectionTestUtils.setField(pipeline, "aiSmartCropService", ai);

        organizer = new MenuImageOrganizer();
        ReflectionTestUtils.setField(organizer, "photoUploadDir", tempDir.toString());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. ImageStandards sanity checks
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Display variant aspect ratio equals 4:3")
    void displayAspectRatio_is_4_3() {
        double ratio = (double) ImageStandards.DISPLAY_WIDTH / ImageStandards.DISPLAY_HEIGHT;
        assertThat(ratio).isCloseTo(4.0 / 3.0, within(0.001));
    }

    @Test
    @DisplayName("Thumbnail variant is square")
    void thumbnail_isSquare() {
        assertThat(ImageStandards.THUMB_SIZE).isEqualTo(ImageStandards.THUMB_SIZE); // trivially true
        // More meaningful: via the enum
        assertThat(ImageVariant.THUMBNAIL.width).isEqualTo(ImageVariant.THUMBNAIL.height);
    }

    @Test
    @DisplayName("DISPLAY quality is between 0.7 and 0.95 (good-quality JPEG)")
    void displayQuality_inRange() {
        assertThat(ImageStandards.DISPLAY_QUALITY)
                .isBetween(0.70f, 0.95f);
    }

    @Test
    @DisplayName("Max upload size is at least 5 MB")
    void maxUploadSize_atLeast5MB() {
        assertThat(ImageStandards.MAX_UPLOAD_BYTES).isGreaterThanOrEqualTo(5L * 1024 * 1024);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. CropWindow helpers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CropWindow.FULL covers the entire image")
    void cropWindow_full_coversAll() {
        CropWindow w = CropWindow.FULL;
        assertThat(w.x()).isZero();
        assertThat(w.y()).isZero();
        assertThat(w.width()).isEqualTo(1.0);
        assertThat(w.height()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("CropWindow.isReliable() is false below threshold")
    void cropWindow_reliability() {
        CropWindow low  = new CropWindow(0.1, 0.1, 0.8, 0.8, 0.3);
        CropWindow high = new CropWindow(0.1, 0.1, 0.8, 0.8, 0.85);
        assertThat(low.isReliable()).isFalse();
        assertThat(high.isReliable()).isTrue();
    }

    @Test
    @DisplayName("CropWindow.absX/Y/W/H clamp to image bounds")
    void cropWindow_absCoordinates_clampToImage() {
        // crop window slightly outside image (e.g. from a noisy model)
        CropWindow w = new CropWindow(-0.1, -0.1, 1.5, 1.5, 0.9);
        assertThat(w.absX(100)).isGreaterThanOrEqualTo(0);
        assertThat(w.absY(200)).isGreaterThanOrEqualTo(0);
        assertThat(w.absW(100)).isLessThanOrEqualTo(100);
        assertThat(w.absH(200)).isLessThanOrEqualTo(200);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. MenuImageOrganizer – category resolution
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Null / blank category resolves to OTHER")
    void organizer_nullCategory_resolvesToOther() {
        assertThat(organizer.resolveCategory(null)).isEqualTo(MenuImageOrganizer.MenuCategory.OTHER);
        assertThat(organizer.resolveCategory("  ")).isEqualTo(MenuImageOrganizer.MenuCategory.OTHER);
    }

    @Test
    @DisplayName("Known keywords resolve to correct categories")
    void organizer_knownKeywords() {
        assertThat(organizer.resolveCategory("Starters")).isEqualTo(MenuImageOrganizer.MenuCategory.STARTERS);
        assertThat(organizer.resolveCategory("main course")).isEqualTo(MenuImageOrganizer.MenuCategory.MAINS);
        assertThat(organizer.resolveCategory("Desserts & Sweets")).isEqualTo(MenuImageOrganizer.MenuCategory.DESSERTS);
        assertThat(organizer.resolveCategory("Cold Drinks")).isEqualTo(MenuImageOrganizer.MenuCategory.DRINKS);
    }

    @Test
    @DisplayName("Unknown label resolves to OTHER")
    void organizer_unknownLabel_resolvesToOther() {
        assertThat(organizer.resolveCategory("Chef's Specials")).isEqualTo(MenuImageOrganizer.MenuCategory.OTHER);
    }

    @Test
    @DisplayName("toOrganisedPublicUrl builds correct URL")
    void organizer_publicUrl() {
        String url = organizer.toOrganisedPublicUrl("display/abc123.jpg",
                MenuImageOrganizer.MenuCategory.MAINS);
        assertThat(url).isEqualTo("/uploads/photos/display/mains/abc123.jpg");
    }

    @Test
    @DisplayName("MenuCategory.folderName() returns lower-case")
    void menuCategory_folderName_isLowerCase() {
        for (MenuImageOrganizer.MenuCategory c : MenuImageOrganizer.MenuCategory.values()) {
            assertThat(c.folderName()).isEqualTo(c.folderName().toLowerCase());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. ImageProcessingPipeline – visual regression (dimension checks)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DISPLAY variant dimensions match standard (max 800×600)")
    void pipeline_displayVariant_dimensions() throws IOException {
        MockMultipartFile file = makeJpeg(1200, 900);

        ProcessedImageResult result = pipeline.process(file);

        Path displayPath = tempDir.resolve(result.displayFilename());
        assertThat(displayPath).exists();

        BufferedImage out = ImageIO.read(displayPath.toFile());
        assertThat(out.getWidth()).isLessThanOrEqualTo(ImageStandards.DISPLAY_WIDTH);
        assertThat(out.getHeight()).isLessThanOrEqualTo(ImageStandards.DISPLAY_HEIGHT);
    }

    @Test
    @DisplayName("THUMBNAIL variant is exactly 300×300")
    void pipeline_thumbnailVariant_isSquare300() throws IOException {
        MockMultipartFile file = makeJpeg(800, 600);

        ProcessedImageResult result = pipeline.process(file);

        Path thumbPath = tempDir.resolve(result.thumbFilename());
        assertThat(thumbPath).exists();

        BufferedImage out = ImageIO.read(thumbPath.toFile());
        assertThat(out.getWidth()).isEqualTo(ImageStandards.THUMB_SIZE);
        assertThat(out.getHeight()).isEqualTo(ImageStandards.THUMB_SIZE);
    }

    @Test
    @DisplayName("Pipeline produces JPEG output regardless of PNG input")
    void pipeline_outputIsJpeg() throws IOException {
        MockMultipartFile file = makePng(640, 480);

        ProcessedImageResult result = pipeline.process(file);

        assertThat(result.displayFilename()).endsWith(ImageStandards.OUTPUT_EXTENSION);
        assertThat(result.thumbFilename()).endsWith(ImageStandards.OUTPUT_EXTENSION);
    }

    @Test
    @DisplayName("Both variant files are actually written to disk")
    void pipeline_bothFilesExistOnDisk() throws IOException {
        MockMultipartFile file = makeJpeg(1000, 750);

        ProcessedImageResult result = pipeline.process(file);

        assertThat(tempDir.resolve(result.displayFilename())).exists();
        assertThat(tempDir.resolve(result.thumbFilename())).exists();
    }

    @Test
    @DisplayName("Small image (200×150) is not upscaled beyond target width")
    void pipeline_smallImage_notUpscaled() throws IOException {
        MockMultipartFile file = makeJpeg(200, 150);

        ProcessedImageResult result = pipeline.process(file);

        BufferedImage out = ImageIO.read(tempDir.resolve(result.displayFilename()).toFile());
        // Thumbnailator keeps aspect ratio and does not enlarge; width should stay ≤ 800
        assertThat(out.getWidth()).isLessThanOrEqualTo(ImageStandards.DISPLAY_WIDTH);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Validation tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Processing rejects unsupported MIME type")
    void pipeline_rejectsUnsupportedMime() {
        MockMultipartFile bad = new MockMultipartFile(
                "itemPhoto", "file.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> pipeline.process(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    @DisplayName("Processing rejects empty file")
    void pipeline_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile(
                "itemPhoto", "file.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> pipeline.process(empty))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Performance test: pipeline must finish within 3 seconds for a 2 MB image
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pipeline processes a 2 MP image within 3 000 ms")
    void pipeline_performance_within3Seconds() throws IOException {
        MockMultipartFile file = makeJpeg(1600, 1200); // ~2 MP

        long start = System.currentTimeMillis();
        pipeline.process(file);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed)
                .as("Processing should finish within 3 000 ms, took %d ms", elapsed)
                .isLessThan(3_000L);
    }

    @Test
    @DisplayName("Output file size is smaller than the raw input")
    void pipeline_outputFileSize_smallerThanInput() throws IOException {
        MockMultipartFile file = makeJpeg(1200, 900);
        long inputSize = file.getSize();

        ProcessedImageResult result = pipeline.process(file);

        long displaySize = tempDir.resolve(result.displayFilename()).toFile().length();
        long thumbSize   = tempDir.resolve(result.thumbFilename()).toFile().length();

        assertThat(displaySize).isLessThan(inputSize);
        assertThat(thumbSize).isLessThan(inputSize);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private MockMultipartFile makeJpeg(int width, int height) throws IOException {
        return new MockMultipartFile(
                "itemPhoto", "test.jpg", "image/jpeg",
                renderImage(width, height, "jpg"));
    }

    private MockMultipartFile makePng(int width, int height) throws IOException {
        return new MockMultipartFile(
                "itemPhoto", "test.png", "image/png",
                renderImage(width, height, "png"));
    }

    /** Create a simple gradient image and encode it. */
    private byte[] renderImage(int width, int height, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.ORANGE, width, height, Color.RED));
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }
}
