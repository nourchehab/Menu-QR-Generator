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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

        try (var in = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
            return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                .toExternalForm();
        } catch (Exception e) {
            // Fallback to local storage when S3 is not available (missing creds/bucket or network issues)
            log.warn("S3 upload failed for photo, falling back to local storage: {}", e.toString());
            Files.createDirectories(Path.of(localPhotoDir));
            String filename = UUID.randomUUID() + extension;
            Path target = Path.of(localPhotoDir).resolve(filename);
            try (var in2 = file.getInputStream()) {
                Files.copy(in2, target);
            }
            return "/uploads/photos/" + filename;
        }
    }

    public String migrateExistingPhotoPath(String storedPath) throws IOException {
        if (!StringUtils.hasText(storedPath))
            return null;
        if (isRemoteUrl(storedPath))
            return storedPath;

        Path source = resolveExistingFile(storedPath, localPhotoDir);
        if (source == null) {
            // Try to find the object already on S3 (legacy keys / different folder placements)
            String s3Url = tryFindOnS3(storedPath);
            if (s3Url != null) return s3Url;
            // If not found locally or on S3, log and return original path so migration can continue safely.
            log.warn("Could not resolve existing photo path: {} — skipping migration for this item", storedPath);
            return storedPath;
        }

        String extension = getExtension(source.getFileName().toString(), Files.probeContentType(source));
        String key = buildKey(photosFolder, extension);
        String contentType = defaultContentType(Files.probeContentType(source));

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(source));
            return s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                    .toExternalForm();
        } catch (Exception e) {
            log.warn("S3 migration upload failed for photo [{}], falling back to local copy: {}", source, e.toString());
            Files.createDirectories(Path.of(localPhotoDir));
            String filename = UUID.randomUUID() + extension;
            Path target = Path.of(localPhotoDir).resolve(filename);
            Files.copy(source, target);
            return "/uploads/photos/" + filename;
        }
    }

    public String migrateExistingLogoPath(String storedPath) throws IOException {
        if (!StringUtils.hasText(storedPath))
            return null;
        if (isRemoteUrl(storedPath))
            return storedPath;

        Path source = resolveExistingFile(storedPath, localLogoDir);
        if (source == null) {
            // Try to find the logo already on S3 under common keys
            String s3Url = tryFindOnS3(storedPath);
            if (s3Url != null) return s3Url;
            log.warn("Could not resolve existing logo path: {} — skipping migration for this restaurant", storedPath);
            return storedPath;
        }

        String extension = getExtension(source.getFileName().toString(), Files.probeContentType(source));
        String key = buildKey(logosFolder, extension);
        String contentType = defaultContentType(Files.probeContentType(source));

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(source));
            return s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                    .toExternalForm();
        } catch (Exception e) {
            log.warn("S3 migration upload failed for logo [{}], falling back to local copy: {}", source, e.toString());
            Files.createDirectories(Path.of(localLogoDir));
            String filename = UUID.randomUUID() + extension;
            Path target = Path.of(localLogoDir).resolve(filename);
            Files.copy(source, target);
            return "/uploads/logos/" + filename;
        }
    }

    private String tryFindOnS3(String storedPath) {
        if (!StringUtils.hasText(bucketName)) return null;
        String cleaned = storedPath.replace("\\", "/").trim();
        String filename = Paths.get(cleaned).getFileName().toString();

        List<String> candidates = new ArrayList<>();
        candidates.add(cleaned);
        candidates.add(filename);
        candidates.add(logosFolder + "/" + filename);
        candidates.add(photosFolder + "/" + filename);
        // also try common upload subfolders
        candidates.add("display/" + filename);
        candidates.add("thumb/" + filename);

        for (String key : candidates) {
            try {
                HeadObjectRequest head = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.headObject(head);
                // object exists — return its public URL
                return s3Client.utilities()
                        .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                        .toExternalForm();
            } catch (S3Exception e) {
                // not found or access denied — try next candidate
                continue;
            } catch (Exception e) {
                log.debug("S3 lookup failed for key {}: {}", key, e.getMessage());
            }
        }
        return null;
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

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
                    .toExternalForm();
        } catch (Exception e) {
            // Fallback: save to local logo dir and return local path
            log.warn("S3 upload failed for logo, falling back to local storage: {}", e.getMessage());
            Files.createDirectories(Path.of(localLogoDir));
            String filename = UUID.randomUUID() + extension;
            Path target = Path.of(localLogoDir).resolve(filename);
            Files.copy(file.getInputStream(), target);
            return "/uploads/logos/" + filename;
        }
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
