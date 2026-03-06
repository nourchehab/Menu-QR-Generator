package com.restaurant.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {
    
    @Value("${file.upload.logo-dir:uploads/logos}")
    private String logoUploadDir;
    
    @Value("${file.upload.photo-dir:uploads/photos}")
    private String photoUploadDir;
    
    @PostConstruct
    public void init() {
        try {
            // Create upload directories if they don't exist
            Path logoPath = Paths.get(logoUploadDir);
            Path photoPath = Paths.get(photoUploadDir);
            
            if (!Files.exists(logoPath)) {
                Files.createDirectories(logoPath);
                System.out.println("Created logo upload directory: " + logoPath.toAbsolutePath());
            }
            
            if (!Files.exists(photoPath)) {
                Files.createDirectories(photoPath);
                System.out.println("Created photo upload directory: " + photoPath.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories!", e);
        }
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded logos
        registry.addResourceHandler("/uploads/logos/**")
                .addResourceLocations("file:" + logoUploadDir + "/");
        
        // Serve uploaded photos
        registry.addResourceHandler("/uploads/photos/**")
                .addResourceLocations("file:" + photoUploadDir + "/");
    }
}