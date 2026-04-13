package com.restaurant.admin.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import com.restaurant.admin.service.QrCodeService;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final SimpleUserRepository simpleUserRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository     branchRepository;
    private final QrCodeService        qrCodeService;

    @Value("${app.publicBaseUrl:http://localhost:8081}")
    private String publicBaseUrl;

    public QrController(SimpleUserRepository simpleUserRepository,
                        RestaurantRepository restaurantRepository,
                        BranchRepository branchRepository,
                        QrCodeService qrCodeService) {
        this.simpleUserRepository = simpleUserRepository;
        this.restaurantRepository = restaurantRepository;
        this.branchRepository     = branchRepository;
        this.qrCodeService        = qrCodeService;
    }

    /**
     * GET /api/qr/restaurant/{restaurantId}
     * ✅ Primary QR — points to /r/{restaurantId}.
     * /r/ auto-redirects to branch menu if 1 branch, shows picker if multiple.
     */
    @GetMapping(value = "/restaurant/{restaurantId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getRestaurantQr(@PathVariable Long restaurantId) {
        if (restaurantRepository.findById(restaurantId).isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        return pngResponse(publicBaseUrl + "/r/" + restaurantId);
    }

    /**
     * GET /api/qr/branch/{branchId}
     * From branch dashboard — still generates a restaurant-level QR.
     */
    @GetMapping(value = "/branch/{branchId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getBranchQr(@PathVariable Long branchId) {
        var branchOpt = branchRepository.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var branch = branchOpt.get();
        Long restaurantId = branch.getRestaurant() != null ? branch.getRestaurant().getId() : null;
        if (restaurantId == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return pngResponse(publicBaseUrl + "/r/" + restaurantId);
    }

    /** Legacy authenticated endpoint. */
    @GetMapping(value = "/menu", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMenuQr(Authentication authentication) {
        String email = authentication.getName();
        Optional<SimpleUser> userOpt = simpleUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).build();
        Optional<Restaurant> restOpt = restaurantRepository.findFirstByUserOrderByIdDesc(userOpt.get());
        if (restOpt.isEmpty()) return ResponseEntity.status(404).build();
        return pngResponse(publicBaseUrl + "/r/" + restOpt.get().getId());
    }

    /** Legacy public endpoint. */
    @GetMapping(value = "/menu/{restaurantId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMenuQrPublic(@PathVariable Long restaurantId) {
        if (restaurantRepository.findById(restaurantId).isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        return pngResponse(publicBaseUrl + "/r/" + restaurantId);
    }

    private ResponseEntity<byte[]> pngResponse(String url) {
        byte[] png = qrCodeService.generatePngQr(url, 320);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}