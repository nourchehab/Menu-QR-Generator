package com.restaurant.admin.controller;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.service.AiServiceClient;
import com.restaurant.admin.service.BranchService;
import com.restaurant.admin.service.RestaurantService;
import com.restaurant.admin.service.SimpleUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;      
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for RestaurantController — Feature 1: Enter Restaurant Information (SCRUM-219)
 */

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantControllerTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────
    @Mock private RestaurantService restaurantService;
    @Mock private SimpleUserService userService;
    @Mock private BranchRepository  branchRepository;
    @Mock private BranchService     branchService;
    @Mock private AiServiceClient   aiServiceClient;
    @Mock private Principal         mockPrincipal;

    @InjectMocks
    private RestaurantController restaurantController;

    // ── Reusable test objects ─────────────────────────────────────────────────
    private SimpleUser mockUser;
    private Restaurant mockRestaurant;

    /**
     * SimpleUser has no setId() because the DB auto-generates it.
     * We use reflection to set it during tests only — this is a common testing pattern.
     */
    private void setUserId(SimpleUser user, Long id) throws Exception {
        Field field = SimpleUser.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        // A logged-in user that has NOT yet set up a restaurant
        mockUser = new SimpleUser();
        setUserId(mockUser, 1L);
        mockUser.setEmail("test@example.com");
        mockUser.setPassword("password123");
        mockUser.setRestaurantSetupComplete(false);

        // A restaurant returned after a successful save
        mockRestaurant = new Restaurant();
        mockRestaurant.setId(10L);
        mockRestaurant.setRestaurantName("Test Restaurant");
        mockRestaurant.setRestaurantType("Fast Food");
        mockRestaurant.setUser(mockUser);

        // By default, principal resolves to our test user's email
        when(mockPrincipal.getName()).thenReturn("test@example.com");
        when(userService.findByEmail("test@example.com")).thenReturn(mockUser);
    }

    // =========================================================================
    // ✅ HAPPY PATH TESTS
    // =========================================================================

    @Test
    @DisplayName("✅ TC-01 | Valid name + type + logo → 200 OK with redirectUrl")
    void testSuccessfulSetupWithLogo() throws Exception {
        MultipartFile logo = new MockMultipartFile(
                "logo", "logo.png", "image/png", new byte[]{1, 2, 3});

        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(1L, "Test Restaurant", "Fast Food", logo))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", logo, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(true,           body.get("success"));
        assertEquals(10L,            body.get("restaurantId"));
        assertEquals("/restaurants", body.get("redirectUrl"));
    }

    @Test
    @DisplayName("✅ TC-02 | Valid name + type, no logo → 200 OK (logo is optional)")
    void testSuccessfulSetupWithoutLogo() throws Exception {
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(eq(1L), eq("Burger Palace"), eq("Casual Dining"), isNull()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Burger Palace", "Casual Dining", null, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
    }

    @Test
    @DisplayName("✅ TC-03 | logoUpload used when logo field is empty → 200 OK")
    void testSetupWithLogoUploadFallback() throws Exception {
        MultipartFile emptyLogo  = new MockMultipartFile("logo", new byte[0]);
        MultipartFile logoUpload = new MockMultipartFile(
                "logoUpload", "upload.png", "image/png", new byte[]{5, 6, 7});

        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(1L, "Sushi Place", "Japanese", logoUpload))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Sushi Place", "Japanese", emptyLogo, logoUpload, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(restaurantService).setupRestaurant(1L, "Sushi Place", "Japanese", logoUpload);
    }

    // =========================================================================
    // 🔐 AUTHENTICATION TESTS
    // =========================================================================

    @Test
    @DisplayName("🔐 TC-04 | No principal (not logged in) → 401 UNAUTHORIZED")
    void testSetupWithNoPrincipal() {
        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body.get("error"));
    }

    @Test
    @DisplayName("🔐 TC-05 | Principal exists but user not in DB → 401 UNAUTHORIZED")
    void testSetupWhenUserNotFound() {
        when(userService.findByEmail("test@example.com")).thenReturn(null);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", null, null, mockPrincipal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // =========================================================================
    // ♻️ DUPLICATE RESTAURANT TESTS
    // =========================================================================

    @Test
    @DisplayName("♻️ TC-06 | User already has a restaurant → still creates new one → 200 OK")
    void testSetupWhenRestaurantAlreadyExists() throws Exception {
        mockUser.setRestaurantSetupComplete(true);
        when(restaurantService.setupRestaurant(1L, "Test Restaurant", "Fast Food", (MultipartFile) isNull()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", null, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true,           body.get("success"));
        assertEquals("Restaurant saved successfully", body.get("message"));
        assertEquals(10L,            body.get("restaurantId"));
        assertEquals("/restaurants", body.get("redirectUrl"));

        // Ensure a new restaurant was created
        verify(restaurantService).setupRestaurant(1L, "Test Restaurant", "Fast Food", (MultipartFile) isNull());
    }

    // =========================================================================
    // ❌ ERROR / EDGE CASE TESTS
    // =========================================================================

    @Test
    @DisplayName("❌ TC-07 | Service throws exception → 500 INTERNAL SERVER ERROR")
    void testSetupWhenServiceThrowsException() throws Exception {
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("S3 upload failed"));

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", null, null, mockPrincipal);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(body.get("error").toString().contains("S3 upload failed"));
    }

    @Test
    @DisplayName("❌ TC-08 | Empty restaurant name → forwarded to service (frontend validates)")
    void testSetupWithEmptyRestaurantName() throws Exception {
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(eq(1L), eq(""), eq("Fast Food"), any()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "", "Fast Food", null, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("❌ TC-09 | Very long restaurant name (300 chars) → service still called")
    void testSetupWithVeryLongRestaurantName() throws Exception {
        String longName = "A".repeat(300);
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(eq(1L), eq(longName), eq("Fast Food"), any()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                longName, "Fast Food", null, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("❌ TC-10 | Restaurant name with special characters → 200 OK")
    void testSetupWithSpecialCharactersInName() throws Exception {
        String specialName = "Café & Bistro <Le Bon>";
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(eq(1L), eq(specialName), eq("Café"), any()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                specialName, "Café", null, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // =========================================================================
    // 🖼️ LOGO TESTS
    // =========================================================================

    @Test
    @DisplayName("🖼️ TC-11 | Wrong file type (PDF) as logo → forwarded to service")
    void testSetupWithPdfAsLogo() throws Exception {
        MultipartFile pdfFile = new MockMultipartFile(
                "logo", "document.pdf", "application/pdf", new byte[]{1, 2, 3});

        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(1L, "Test", "Fast Food", pdfFile))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test", "Fast Food", pdfFile, null, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("🖼️ TC-12 | Both logo and logoUpload provided → logo field takes priority")
    void testLogoFieldTakesPriorityOverLogoUpload() throws Exception {
        MultipartFile primaryLogo  = new MockMultipartFile(
                "logo",       "primary.png",  "image/png", new byte[]{1});
        MultipartFile fallbackLogo = new MockMultipartFile(
                "logoUpload", "fallback.png", "image/png", new byte[]{2});

        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(1L, "Test", "Fast Food", primaryLogo))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test", "Fast Food", primaryLogo, fallbackLogo, mockPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(restaurantService).setupRestaurant(1L, "Test", "Fast Food", primaryLogo);
    }

    // =========================================================================
    // 📦 RESPONSE STRUCTURE TESTS
    // =========================================================================

    @Test
    @DisplayName("📦 TC-13 | Success response contains all expected fields")
    void testSuccessResponseStructure() throws Exception {
        when(restaurantService.userHasRestaurant(mockUser)).thenReturn(false);
        when(restaurantService.setupRestaurant(any(), any(), any(), any()))
                .thenReturn(mockRestaurant);

        ResponseEntity<?> response = restaurantController.handleSetup(
                "Test Restaurant", "Fast Food", null, null, mockPrincipal);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("success"),      "Response must have 'success'");
        assertTrue(body.containsKey("message"),      "Response must have 'message'");
        assertTrue(body.containsKey("restaurantId"), "Response must have 'restaurantId'");
        assertTrue(body.containsKey("redirectUrl"),  "Response must have 'redirectUrl'");
    }
}