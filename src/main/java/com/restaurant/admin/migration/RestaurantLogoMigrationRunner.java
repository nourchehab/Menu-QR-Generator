package com.restaurant.admin.migration;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.service.S3PhotoStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RestaurantLogoMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RestaurantLogoMigrationRunner.class);

    private final RestaurantRepository restaurantRepository;
    private final S3PhotoStorageService s3PhotoStorageService;

    @Value("${migration.logos.enabled:false}")
    private boolean logoMigrationEnabled;

    public RestaurantLogoMigrationRunner(RestaurantRepository restaurantRepository,
            S3PhotoStorageService s3PhotoStorageService) {
        this.restaurantRepository = restaurantRepository;
        this.s3PhotoStorageService = s3PhotoStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!logoMigrationEnabled)
            return;

        log.info("Starting restaurant logo migration to S3...");
        List<Restaurant> restaurants = restaurantRepository.findAll();

        int migrated = 0, skipped = 0, failed = 0;
        for (Restaurant restaurant : restaurants) {
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

        log.info("Logo migration finished. migrated={}, skipped={}, failed={}", migrated, skipped, failed);
    }
}
