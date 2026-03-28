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

import com.restaurant.admin.model.Branch;
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
     * GET /api/qr/menu
     * Legacy authenticated endpoint — generates QR for user's most-recent restaurant.
     * Kept for backwards compatibility.
     */
    @GetMapping(value = "/menu", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMenuQr(Authentication authentication) {
        String email = authentication.getName();

        Optional<SimpleUser> userOpt = simpleUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).build();

        Optional<Restaurant> restaurantOpt = restaurantRepository.findFirstByUserOrderByIdDesc(userOpt.get());
        if (restaurantOpt.isEmpty()) return ResponseEntity.status(404).build();

        String url = publicBaseUrl + "/menu/" + restaurantOpt.get().getId();
        return pngResponse(url);
    }

    /**
     * GET /api/qr/menu/{restaurantId}
     * Public QR for a restaurant (old QR codes still work via /menu/{restaurantId}).
     */
    @GetMapping(value = "/menu/{restaurantId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMenuQrPublic(@PathVariable Long restaurantId) {
        if (restaurantRepository.findById(restaurantId).isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        String url = publicBaseUrl + "/menu/" + restaurantId;
        return pngResponse(url);
    }

    /**
     * GET /api/qr/branch/{branchId}
     * ✅ Branch-scoped QR — points to /b/{branchId} (short URL, no path conflict).
     * This is what the branch dashboard QR button uses.
     * No authentication required so anyone can scan.
     */
    @GetMapping(value = "/branch/{branchId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getBranchQr(@PathVariable Long branchId) {
        Optional<Branch> branchOpt = branchRepository.findById(branchId);
        if (branchOpt.isEmpty()) return ResponseEntity.status(404).build();

        // ✅ Points to /b/{branchId} — short, conflict-free public URL
        String url = publicBaseUrl + "/b/" + branchId;
        return pngResponse(url);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

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