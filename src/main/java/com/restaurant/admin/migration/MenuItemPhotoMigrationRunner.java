package com.restaurant.admin.migration;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.service.MenuItemImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class MenuItemPhotoMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuItemPhotoMigrationRunner.class);

    private final MenuItemRepository menuItemRepository;
    private final MenuItemImageStorageService menuItemImageStorageService;

    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;

    public MenuItemPhotoMigrationRunner(MenuItemRepository menuItemRepository,
            MenuItemImageStorageService menuItemImageStorageService) {
        this.menuItemRepository = menuItemRepository;
        this.menuItemImageStorageService = menuItemImageStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!migrationEnabled)
            return;

        log.info("Starting menu item photo migration to S3...");
        List<MenuItem> items = menuItemRepository.findAll();

        int migrated = 0, skipped = 0, failed = 0;
        for (MenuItem item : items) {
            String current = item.getPhotoPath();
            if (current == null || current.isBlank()) {
                skipped++;
                continue;
            }
            if (current.startsWith("http://") || current.startsWith("https://")) {
                skipped++;
                continue;
            }

            try {
                String s3Url = menuItemImageStorageService.migratePhotoPathToS3(current);
                item.setPhotoPath(s3Url);
                menuItemRepository.save(item);
                migrated++;
                log.info("Migrated id={} -> {}", item.getId(), s3Url);
            } catch (Exception ex) {
                failed++;
                log.error("Failed migrating id={} path={}", item.getId(), current, ex);
            }
        }

        log.info("Migration finished. migrated={}, skipped={}, failed={}", migrated, skipped, failed);
    }
}
