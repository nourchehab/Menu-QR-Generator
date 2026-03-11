package com.restaurant.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class MenuItemImageStorageService {

    private final S3PhotoStorageService s3PhotoStorageService;

    @Autowired
    public MenuItemImageStorageService(S3PhotoStorageService s3PhotoStorageService) {
        this.s3PhotoStorageService = s3PhotoStorageService;
    }

    public String storePhoto(MultipartFile file) throws IOException {
        return s3PhotoStorageService.uploadNewPhoto(file);
    }

    public void deletePhoto(String photoPath) {
        s3PhotoStorageService.deleteIfS3Url(photoPath);
    }

    public String migratePhotoPathToS3(String existingPhotoPath) throws IOException {
        return s3PhotoStorageService.migrateExistingPhotoPath(existingPhotoPath);
    }

    /**
     * Delete an image file (any variant). Legacy method used by older code.
     */
    public void deleteIfExists(String storedValue) {
        deletePhoto(storedValue);
    }

    /**
     * Convert a stored relative path to a public URL.
     * Preserves backwards compatibility for old flat files / directory structures,
     * while correctly returning the S3 URL for new items.
     */
    public String toPublicUrl(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank())
            return null;
        if (storedFilename.startsWith("http://") || storedFilename.startsWith("https://"))
            return storedFilename;
        if (storedFilename.startsWith("/uploads/"))
            return storedFilename;
        return "/uploads/photos/" + storedFilename;
    }

    public String toThumbPublicUrl(String thumbPath) {
        return toPublicUrl(thumbPath);
    }
}
