package com.restaurant.admin.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import com.restaurant.admin.service.QrCodeService;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final SimpleUserRepository simpleUserRepository;
    private final RestaurantRepository restaurantRepository;
    private final QrCodeService qrCodeService;

    // You can override in application.properties: app.publicBaseUrl=http://localhost:8081
    @Value("${app.publicBaseUrl:http://localhost:8081}")
    private String publicBaseUrl;

    public QrController(
            SimpleUserRepository simpleUserRepository,
            RestaurantRepository restaurantRepository,
            QrCodeService qrCodeService) {
        this.simpleUserRepository = simpleUserRepository;
        this.restaurantRepository = restaurantRepository;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping(value = "/menu", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMenuQr(Authentication authentication) {

        String email = authentication.getName();

        Optional<SimpleUser> userOpt = simpleUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Optional<Restaurant> restaurantOpt = restaurantRepository.findByUser(userOpt.get());
        if (restaurantOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Long restaurantId = restaurantOpt.get().getId();
        String url = publicBaseUrl + "/menu/" + restaurantId;

        byte[] png = qrCodeService.generatePngQr(url, 320);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
    @GetMapping(value = "/menu/{restaurantId}", produces = MediaType.IMAGE_PNG_VALUE)
public ResponseEntity<byte[]> getMenuQrPublic(@PathVariable Long restaurantId) {
    Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
    if (restaurantOpt.isEmpty()) {
        return ResponseEntity.status(404).build();
    }

    String url = publicBaseUrl + "/menu/" + restaurantId;
    byte[] png = qrCodeService.generatePngQr(url, 320);

    HttpHeaders headers = new HttpHeaders();
    headers.setCacheControl(CacheControl.noStore());

    return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.IMAGE_PNG)
            .body(png);
}
}