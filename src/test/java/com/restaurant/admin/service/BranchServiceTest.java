package com.restaurant.admin.service;

import com.restaurant.admin.model.Branch;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.BranchRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BranchService
 * Tests CRUD operations and security checks (user ownership verification)
 */
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private BranchService branchService;

    private SimpleUser testUser;
    private Restaurant testRestaurant;
    private Branch testBranch;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup test data
        testUser = new SimpleUser();
        testUser.setEmail("admin@test.com");

        testRestaurant = new Restaurant("Test Restaurant", "cafe", testUser);
        testRestaurant.setId(1L);

        testBranch = new Branch("Downtown", "123 Main St", "555-0001", testRestaurant);
        testBranch.setId(1L);
    }

    // ────────────────────────────────────────────────────────────────────────
    // CREATE TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBranch should save branch successfully when user owns restaurant")
    void createBranch_success() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.save(any(Branch.class))).thenReturn(testBranch);

        // Act
        Branch result = branchService.createBranch(testUser, "Downtown", "123 Main St", "555-0001");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBranchName()).isEqualTo("Downtown");
        verify(branchRepository).save(any(Branch.class));
    }

    @Test
    @DisplayName("createBranch should throw SecurityException when user has no restaurant")
    void createBranch_noRestaurant_throwsSecurityException() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> branchService.createBranch(testUser, "Downtown", "123 Main St", "555-0001"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("User has no restaurant");

        verify(branchRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // READ TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveBranchesForUser should return only active branches")
    void getActiveBranchesForUser_success() {
        // Arrange
        Branch activeBranch1 = new Branch("Downtown", "123 Main", "555-0001", testRestaurant);
        Branch activeBranch2 = new Branch("Midtown", "456 Second", "555-0002", testRestaurant);
        List<Branch> activeBranches = List.of(activeBranch1, activeBranch2);

        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByRestaurantAndIsActiveTrue(testRestaurant)).thenReturn(activeBranches);

        // Act
        List<Branch> result = branchService.getActiveBranchesForUser(testUser);

        // Assert
        assertThat(result).hasSize(2).containsExactly(activeBranch1, activeBranch2);
        verify(branchRepository).findByRestaurantAndIsActiveTrue(testRestaurant);
    }

    @Test
    @DisplayName("getAllBranchesForUser should return active and inactive branches")
    void getAllBranchesForUser_success() {
        // Arrange
        Branch activeBranch = new Branch("Downtown", "123 Main", "555-0001", testRestaurant);
        activeBranch.setActive(true);
        Branch inactiveBranch = new Branch("Uptown", "789 Third", "555-0003", testRestaurant);
        inactiveBranch.setActive(false);
        List<Branch> allBranches = List.of(activeBranch, inactiveBranch);

        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByRestaurant(testRestaurant)).thenReturn(allBranches);

        // Act
        List<Branch> result = branchService.getAllBranchesForUser(testUser);

        // Assert
        assertThat(result).hasSize(2);
        verify(branchRepository).findByRestaurant(testRestaurant);
    }

    @Test
    @DisplayName("getBranchForUser should return branch if user owns it")
    void getBranchForUser_success() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.of(testBranch));

        // Act
        Optional<Branch> result = branchService.getBranchForUser(testUser, 1L);

        // Assert
        assertThat(result).isPresent().contains(testBranch);
        verify(branchRepository).findByIdAndRestaurant(1L, testRestaurant);
    }

    @Test
    @DisplayName("getBranchForUser should throw exception for non-existent branch")
    void getBranchForUser_branchNotFound() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(999L, testRestaurant)).thenReturn(Optional.empty());

        // Act
        Optional<Branch> result = branchService.getBranchForUser(testUser, 999L);

        // Assert
        assertThat(result).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // UPDATE TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateBranch should update branch details successfully")
    void updateBranch_success() {
        // Arrange
        Branch updatedBranch = new Branch("Downtown Updated", "999 New Ave", "555-9999", testRestaurant);
        updatedBranch.setId(1L);

        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.of(testBranch));
        when(branchRepository.save(any(Branch.class))).thenReturn(updatedBranch);

        // Act
        Branch result = branchService.updateBranch(testUser, 1L, "Downtown Updated", "999 New Ave", "555-9999");

        // Assert
        assertThat(result.getBranchName()).isEqualTo("Downtown Updated");
        assertThat(result.getAddress()).isEqualTo("999 New Ave");
        assertThat(result.getPhone()).isEqualTo("555-9999");
        verify(branchRepository).save(any(Branch.class));
    }

    @Test
    @DisplayName("updateBranch should throw exception if branch not owned by user")
    void updateBranch_notOwned_throwsException() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> branchService.updateBranch(testUser, 1L, "New Name", "New Address", "555-0000"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not owned by user");

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleBranchStatus should flip active status")
    void toggleBranchStatus_success() {
        // Arrange
        testBranch.setActive(true);
        Branch toggledBranch = new Branch("Downtown", "123 Main St", "555-0001", testRestaurant);
        toggledBranch.setId(1L);
        toggledBranch.setActive(false);

        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.of(testBranch));
        when(branchRepository.save(any(Branch.class))).thenReturn(toggledBranch);

        // Act
        Branch result = branchService.toggleBranchStatus(testUser, 1L);

        // Assert
        assertThat(result.isActive()).isFalse();
        verify(branchRepository).save(any(Branch.class));
    }

    // ────────────────────────────────────────────────────────────────────────
    // DELETE TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteBranch should delete branch successfully")
    void deleteBranch_success() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.of(testBranch));
        doNothing().when(branchRepository).delete(testBranch);

        // Act
        branchService.deleteBranch(testUser, 1L);

        // Assert
        verify(branchRepository).delete(testBranch);
    }

    @Test
    @DisplayName("deleteBranch should throw exception if branch not owned by user")
    void deleteBranch_notOwned_throwsException() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.findByIdAndRestaurant(1L, testRestaurant)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> branchService.deleteBranch(testUser, 1L))
                .isInstanceOf(SecurityException.class);

        verify(branchRepository, never()).delete(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // MULTI-BRANCH FLAG TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isMultiBranch should return true when restaurant has > 1 branch")
    void isMultiBranch_true() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.countByRestaurant(testRestaurant)).thenReturn(3L);

        // Act
        boolean result = branchService.isMultiBranch(testUser);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isMultiBranch should return false when restaurant has 1 or 0 branches")
    void isMultiBranch_false() {
        // Arrange
        when(restaurantRepository.findByUser(testUser)).thenReturn(Optional.of(testRestaurant));
        when(branchRepository.countByRestaurant(testRestaurant)).thenReturn(1L);

        // Act
        boolean result = branchService.isMultiBranch(testUser);

        // Assert
        assertThat(result).isFalse();
    }
}
