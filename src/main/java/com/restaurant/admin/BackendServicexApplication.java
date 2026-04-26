package com.restaurant.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BackendServicexApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendServicexApplication.class, args);
    }
}
