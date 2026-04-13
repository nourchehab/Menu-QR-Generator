package com.restaurant.admin.migration;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.service.MenuItemImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Component
public class MenuItemPhotoMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuItemPhotoMigrationRunner.class);

    private final MenuItemRepository menuItemRepository;
    private final MenuItemImageStorageService menuItemImageStorageService;

    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;

    @Value("${migration.batch.size:200}")
    private int migrationBatchSize;

    public MenuItemPhotoMigrationRunner(MenuItemRepository menuItemRepository,
            MenuItemImageStorageService menuItemImageStorageService) {
        this.menuItemRepository = menuItemRepository;
        this.menuItemImageStorageService = menuItemImageStorageService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!migrationEnabled)
            return;

        log.info("Starting menu item image migration to S3 (photoPath + thumbPath) in batches...");

        int migrated = 0, skipped = 0, failed = 0;

        Pageable pageable = PageRequest.of(0, Math.max(1, migrationBatchSize));
        while (true) {
            Page<MenuItem> page = menuItemRepository.findAll(pageable);
            if (page == null || page.getContent().isEmpty()) {
                break;
            }

            for (MenuItem item : page.getContent()) {
                String currentPhoto = item.getPhotoPath();
                String currentThumb = item.getThumbPath();

                boolean photoNeedsMigration = needsMigration(currentPhoto);
                boolean thumbNeedsMigration = needsMigration(currentThumb);

                if (!photoNeedsMigration && !thumbNeedsMigration) {
                    skipped++;
                    continue;
                }

                try {
                    // If both fields point to the same legacy file, migrate once and reuse URL.
                    if (photoNeedsMigration && thumbNeedsMigration && currentPhoto.equals(currentThumb)) {
                        String s3Url = menuItemImageStorageService.migratePhotoPathToS3(currentPhoto);
                        item.setPhotoPath(s3Url);
                        item.setThumbPath(s3Url);
                        migrated++;
                    } else {
                        if (photoNeedsMigration) {
                            String photoS3Url = menuItemImageStorageService.migratePhotoPathToS3(currentPhoto);
                            item.setPhotoPath(photoS3Url);
                            migrated++;
                        }
                        if (thumbNeedsMigration) {
                            String thumbS3Url = menuItemImageStorageService.migratePhotoPathToS3(currentThumb);
                            item.setThumbPath(thumbS3Url);
                            migrated++;
                        }
                    }

                    menuItemRepository.save(item);
                    log.info("Migrated menu item id={} photoPath={} thumbPath= {}",
                            item.getId(), item.getPhotoPath(), item.getThumbPath());
                } catch (Exception ex) {
                    failed++;
                    log.error("Failed migrating menu item id={} photoPath={} thumbPath={}",
                            item.getId(), currentPhoto, currentThumb, ex);
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        log.info("Menu image migration finished. migratedFields={}, skippedItems={}, failedItems={}",
                migrated, skipped, failed);
    }

    private boolean needsMigration(String path) {
        return path != null
                && !path.isBlank()
                && !path.startsWith("http://")
                && !path.startsWith("https://");
    }
}
