package com.restaurant.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AdminDashboardController
 * Tests REST endpoints with real Spring context and database
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminDashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleUserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private SimpleUser testUser;
    private Restaurant testRestaurant;
    private Branch testBranch1;
    private Branch testBranch2;

    @BeforeEach
    void setUp() {
        // Clean up
        branchRepository.deleteAll();
        restaurantRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new SimpleUser();
        testUser.setEmail("admin@test.com");
        testUser.setPassword("password123");
        testUser.setRestaurantSetupComplete(true);
        testUser = userRepository.save(testUser);

        // Create restaurant
        testRestaurant = new Restaurant("Test Restaurant", "cafe", testUser);
        testRestaurant.setLogoPath("/uploads/logos/test.jpg");
        testRestaurant.setMenuBackgroundColor("#ffffff");
        testRestaurant = restaurantRepository.save(testRestaurant);

        // Create branches
        testBranch1 = new Branch("Downtown", "123 Main St", "555-0001", testRestaurant);
        testBranch1.setActive(true);
        testBranch1 = branchRepository.save(testBranch1);

        testBranch2 = new Branch("Midtown", "456 Second Ave", "555-0002", testRestaurant);
        testBranch2.setActive(true);
        testBranch2 = branchRepository.save(testBranch2);
    }

    // ────────────────────────────────────────────────────────────────────────
    // DASHBOARD DATA ENDPOINT TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/restaurants/dashboard should return restaurant with branches")
    void getDashboardData_success() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/dashboard")
                        .with(user(testUser.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testRestaurant.getId()))
                .andExpect(jsonPath("$.data.restaurantName").value("Test Restaurant"))
                .andExpect(jsonPath("$.data.isMultiBranch").value(true))
                .andExpect(jsonPath("$.data.branches.length()").value(2))
                .andExpect(jsonPath("$.data.branches[0].branchName").value("Downtown"))
                .andExpect(jsonPath("$.data.branches[1].branchName").value("Midtown"));
    }

    @Test
    @DisplayName("GET /api/admin/restaurants/dashboard should return isMultiBranch=false for single branch")
    void getDashboardData_singleBranch_notMultiBranch() throws Exception {
        // Remove one branch
        branchRepository.delete(testBranch2);

        mockMvc.perform(get("/api/admin/restaurants/dashboard")
                        .with(user(testUser.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isMultiBranch").value(false))
                .andExpect(jsonPath("$.data.branches.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/restaurants/dashboard should return 401 when not authenticated")
    void getDashboardData_notAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CREATE BRANCH TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/branches should create a new branch")
    void createBranch_success() throws Exception {
        Map<String, String> payload = Map.of(
                "branchName", "Uptown",
                "address", "789 Third St",
                "phone", "555-0003"
        );

        mockMvc.perform(post("/api/admin/branches")
                        .with(user(testUser.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branchName").value("Uptown"))
                .andExpect(jsonPath("$.data.address").value("789 Third St"))
                .andExpect(jsonPath("$.data.phone").value("555-0003"));

        // Verify saved to DB
        assertThat(branchRepository.findByRestaurant(testRestaurant)).hasSize(3);
    }

    @Test
    @DisplayName("POST /api/admin/branches should return 400 for missing required fields")
    void createBranch_missingFields_returns400() throws Exception {
        Map<String, String> payload = Map.of(
                "branchName", "Uptown"
                // missing address and phone
        );

        mockMvc.perform(post("/api/admin/branches")
                        .with(user(testUser.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/admin/branches should return 401 when not authenticated")
    void createBranch_notAuthenticated_returns401() throws Exception {
        Map<String, String> payload = Map.of(
                "branchName", "Uptown",
                "address", "789 Third St",
                "phone", "555-0003"
        );

        mockMvc.perform(post("/api/admin/branches")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    // ────────────────────────────────────────────────────────────────────────
    // GET BRANCH TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/branches/:id should return branch details")
    void getBranch_success() throws Exception {
        mockMvc.perform(get("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(testUser.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testBranch1.getId()))
                .andExpect(jsonPath("$.data.branchName").value("Downtown"))
                .andExpect(jsonPath("$.data.address").value("123 Main St"));
    }

    @Test
    @DisplayName("GET /api/admin/branches/:id should return 403 for unauthorized user")
    void getBranch_differentUser_returns403() throws Exception {
        // Create another user
        SimpleUser otherUser = new SimpleUser();
        otherUser.setEmail("other@test.com");
        otherUser.setPassword("password123");
        userRepository.save(otherUser);

        mockMvc.perform(get("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(otherUser.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ────────────────────────────────────────────────────────────────────────
    // UPDATE BRANCH TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/admin/branches/:id should update branch details")
    void updateBranch_success() throws Exception {
        Map<String, String> payload = Map.of(
                "branchName", "Downtown Updated",
                "address", "999 Updated Ave",
                "phone", "555-9999"
        );

        mockMvc.perform(put("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(testUser.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branchName").value("Downtown Updated"))
                .andExpect(jsonPath("$.data.address").value("999 Updated Ave"));

        // Verify saved to DB
        Branch updated = branchRepository.findById(testBranch1.getId()).orElseThrow();
        assertThat(updated.getBranchName()).isEqualTo("Downtown Updated");
    }

    @Test
    @DisplayName("PUT /api/admin/branches/:id should return 400 for missing required fields")
    void updateBranch_missingFields_returns400() throws Exception {
        Map<String, String> payload = Map.of(
                "branchName", "Downtown Updated"
                // missing address and phone
        );

        mockMvc.perform(put("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(testUser.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────────────────────────────
    // DELETE BRANCH TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/admin/branches/:id should delete branch")
    void deleteBranch_success() throws Exception {
        mockMvc.perform(delete("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(testUser.getEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify deleted from DB
        assertThat(branchRepository.findById(testBranch1.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/admin/branches/:id should return 403 for unauthorized user")
    void deleteBranch_differentUser_returns403() throws Exception {
        SimpleUser otherUser = new SimpleUser();
        otherUser.setEmail("other@test.com");
        otherUser.setPassword("password123");
        userRepository.save(otherUser);

        mockMvc.perform(delete("/api/admin/branches/{id}", testBranch1.getId())
                        .with(user(otherUser.getEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Verify NOT deleted
        assertThat(branchRepository.findById(testBranch1.getId())).isPresent();
    }

    // ────────────────────────────────────────────────────────────────────────
    // SECURITY & ACCESS CONTROL TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin dashboard should not leak data for unauthorized users")
    void dashboardDataIsUserScoped() throws Exception {
        // Create another user with their own restaurant
        SimpleUser otherUser = new SimpleUser();
        otherUser.setEmail("other.admin@test.com");
        otherUser.setPassword("password123");
        otherUser = userRepository.save(otherUser);

        Restaurant otherRestaurant = new Restaurant("Other Restaurant", "buffet", otherUser);
        restaurantRepository.save(otherRestaurant);

        Branch otherBranch = new Branch("Other Branch", "Other Address", "555-0099", otherRestaurant);
        branchRepository.save(otherBranch);

        // Authenticate as first user and verify they only see their restaurant
        mockMvc.perform(get("/api/admin/restaurants/dashboard")
                        .with(user(testUser.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantName").value("Test Restaurant"))
                .andExpect(jsonPath("$.data.id").value(testRestaurant.getId()));

        // Verify they don't see other restaurant
        mockMvc.perform(get("/api/admin/restaurants/dashboard")
                        .with(user(testUser.getEmail()).roles("USER")))
                .andExpect(jsonPath("$.data.id").value(testRestaurant.getId()))
                .andExpect(status().isOk());
    }
}
