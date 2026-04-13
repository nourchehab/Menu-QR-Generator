package com.restaurant.admin.migration;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.service.S3PhotoStorageService;
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
public class RestaurantLogoMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RestaurantLogoMigrationRunner.class);

    private final RestaurantRepository restaurantRepository;
    private final S3PhotoStorageService s3PhotoStorageService;

    @Value("${migration.logos.enabled:false}")
    private boolean logoMigrationEnabled;

    @Value("${migration.batch.size:200}")
    private int migrationBatchSize;

    public RestaurantLogoMigrationRunner(RestaurantRepository restaurantRepository,
            S3PhotoStorageService s3PhotoStorageService) {
        this.restaurantRepository = restaurantRepository;
        this.s3PhotoStorageService = s3PhotoStorageService;
    }

    @Override
    public void run(String... args) {
        if (!logoMigrationEnabled)
            return;

        log.info("Starting restaurant logo migration to S3 (batched)...");

        int migrated = 0, skipped = 0, failed = 0;

        Pageable pageable = PageRequest.of(0, Math.max(1, migrationBatchSize));
        while (true) {
            Page<Restaurant> page = restaurantRepository.findAll(pageable);
            if (page == null || page.getContent().isEmpty()) {
                break;
            }

            for (Restaurant restaurant : page.getContent()) {
                String current = restaurant.getLogoPath();
                if (current == null || current.isBlank()) {
                    skipped++;
                    continue;
                }
                if (current.startsWith("http://") || current.startsWith("https://")) {
                    skipped++;
                    continue;
                }

                try {
                    String s3Url = s3PhotoStorageService.migrateExistingLogoPath(current);
                    restaurant.setLogoPath(s3Url);
                    restaurantRepository.save(restaurant);
                    migrated++;
                    log.info("Migrated restaurant id={} -> {}", restaurant.getId(), s3Url);
                } catch (Exception ex) {
                    failed++;
                    log.error("Failed migrating restaurant id={} path={}", restaurant.getId(), current, ex);
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        log.info("Logo migration finished. migrated={}, skipped={}, failed={}", migrated, skipped, failed);
    }
}
