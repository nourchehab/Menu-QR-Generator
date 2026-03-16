package com.restaurant.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class S3PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3PhotoStorageService.class);

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.photos-folder:photos}")
    private String photosFolder;

    @Value("${aws.s3.logos-folder:photos/logos}")
    private String logosFolder;

    @Value("${file.upload.photo-dir:uploads/photos}")
    private String localPhotoDir;

    @Value("${file.upload.logo-dir:uploads/logos}")
    private String localLogoDir;

    public S3PhotoStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadNewPhoto(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        // Validate file is an image
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Invalid image file type: " + contentType);
        }

        String extension = getExtension(file.getOriginalFilename(), contentType);
        String key = buildKey(photosFolder, extension);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(defaultContentType(contentType))
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                .toExternalForm();
    }

    public String migrateExistingPhotoPath(String storedPath) throws IOException {
        if (!StringUtils.hasText(storedPath))
            return null;
        if (isRemoteUrl(storedPath))
            return storedPath;

        Path source = resolveExistingFile(storedPath, localPhotoDir);
        if (source == null) {
            throw new IOException("Could not resolve existing photo path: " + storedPath);
        }

        String extension = getExtension(source.getFileName().toString(), Files.probeContentType(source));
        String key = buildKey(photosFolder, extension);
        String contentType = defaultContentType(Files.probeContentType(source));

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(source));

        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                .toExternalForm();
    }

    public String migrateExistingLogoPath(String storedPath) throws IOException {
        if (!StringUtils.hasText(storedPath))
            return null;
        if (isRemoteUrl(storedPath))
            return storedPath;

        Path source = resolveExistingFile(storedPath, localLogoDir);
        if (source == null) {
            throw new IOException("Could not resolve existing logo path: " + storedPath);
        }

        String extension = getExtension(source.getFileName().toString(), Files.probeContentType(source));
        String key = buildKey(logosFolder, extension);
        String contentType = defaultContentType(Files.probeContentType(source));

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(source));

        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                .toExternalForm();
    }

    public void deleteIfS3Url(String photoPath) {
        if (!isRemoteUrl(photoPath))
            return;

        try {
            String key = extractKey(photoPath);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to delete S3 object for [{}]: {}", photoPath, ex.getMessage());
        }
    }

    public String uploadNewLogo(MultipartFile file) throws IOException {
        String extension = getExtension(file.getOriginalFilename(), file.getContentType());
        String key = buildKey(logosFolder, extension);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(defaultContentType(file.getContentType()))
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                .toExternalForm();
    }

        private Path resolveExistingFile(String storedPath, String fallbackLocalDir) {
        String cleaned = storedPath.replace("\\", "/").trim();
        Path raw = Paths.get(cleaned);
        String withoutLeadingSlash = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;

        List<Path> candidates = new ArrayList<>();
        if (raw.isAbsolute())
            candidates.add(raw.normalize());
        candidates.add(Paths.get(cleaned).normalize());
        candidates.add(Paths.get(withoutLeadingSlash).normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve(cleaned).normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve(withoutLeadingSlash).normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve("src/main/resources/static").resolve(cleaned)
                .normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve("src/main/resources/static")
            .resolve(withoutLeadingSlash).normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve("uploads").resolve(cleaned).normalize());
        candidates.add(Paths.get(System.getProperty("user.dir")).resolve("uploads").resolve(withoutLeadingSlash)
            .normalize());

        if (raw.getFileName() != null) {
            candidates.add(Paths.get(fallbackLocalDir).resolve(raw.getFileName()).normalize());
            candidates.add(
                Paths.get(System.getProperty("user.dir")).resolve(fallbackLocalDir).resolve(raw.getFileName())
                    .normalize());
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate))
                return candidate;
        }

        return null;
    }

    private String buildKey(String folder, String extension) {
        return folder + "/" + UUID.randomUUID() + extension;
    }

    private boolean isRemoteUrl(String value) {
        return StringUtils.hasText(value) && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String extractKey(String photoUrl) {
        URI uri = URI.create(photoUrl);
        String path = uri.getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String getExtension(String fileName, String contentType) {
        if (StringUtils.hasText(fileName) && fileName.contains("."))
            return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        if ("image/png".equalsIgnoreCase(contentType))
            return ".png";
        if ("image/webp".equalsIgnoreCase(contentType))
            return ".webp";
        return ".jpg";
    }

    private String defaultContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "image/jpeg";
    }
}
