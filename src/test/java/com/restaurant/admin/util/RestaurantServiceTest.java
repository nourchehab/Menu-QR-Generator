package com.restaurant.admin.util;

import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import com.restaurant.admin.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private SimpleUserRepository userRepository;
    @Mock private MultipartFile logoFile;
    @InjectMocks private RestaurantService restaurantService;

    private SimpleUser user;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        user = new SimpleUser();
        restaurant = new Restaurant("Burger Place", "Fast Food", user);
    }

    // --- setupRestaurant ---

    @Test
    void setupRestaurant_createsNew_whenNoneExists() throws IOException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(restaurantRepository.findByUser(user)).thenReturn(Optional.empty());
        when(restaurantRepository.save(any())).thenReturn(restaurant);

        Restaurant result = restaurantService.setupRestaurant(1L, "Burger Place", "Fast Food", null);

        assertNotNull(result);
        verify(restaurantRepository).save(any(Restaurant.class));
        assertTrue(user.isRestaurantSetupComplete());
    }

    @Test
    void setupRestaurant_updatesExisting_whenAlreadyExists() throws IOException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(restaurantRepository.findByUser(user)).thenReturn(Optional.of(restaurant));

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        when(restaurantRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        restaurantService.setupRestaurant(1L, "New Name", "Cafe", null);

        Restaurant saved = captor.getValue();
        assertEquals("New Name", saved.getRestaurantName());
        assertEquals("Cafe", saved.getRestaurantType());
    }

    @Test
    void setupRestaurant_throwsException_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> restaurantService.setupRestaurant(99L, "X", "Y", null));
    }

    @Test
    void setupRestaurant_skipsLogo_whenLogoFileIsNull() throws IOException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(restaurantRepository.findByUser(user)).thenReturn(Optional.empty());
        when(restaurantRepository.save(any())).thenReturn(restaurant);

        restaurantService.setupRestaurant(1L, "Burger Place", "Fast Food", null);

        assertNull(restaurant.getLogoPath());
    }

    @Test
    void setupRestaurant_skipsLogo_whenLogoFileIsEmpty() throws IOException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(restaurantRepository.findByUser(user)).thenReturn(Optional.empty());
        when(restaurantRepository.save(any())).thenReturn(restaurant);
        when(logoFile.isEmpty()).thenReturn(true);

        restaurantService.setupRestaurant(1L, "Burger Place", "Fast Food", logoFile);

        assertNull(restaurant.getLogoPath());
    }

    // --- Queries ---

    @Test
    void getRestaurantByUserId_returnsEmpty_whenNotFound() {
        when(restaurantRepository.findByUserId(42L)).thenReturn(Optional.empty());
        assertTrue(restaurantService.getRestaurantByUserId(42L).isEmpty());
    }

    @Test
    void userHasRestaurant_returnsFalse_whenNoRestaurant() {
        when(restaurantRepository.existsByUser(user)).thenReturn(false);
        assertFalse(restaurantService.userHasRestaurant(user));
    }

    @Test
    void getLogoPath_returnsCorrectPath() {
        String path = restaurantService.getLogoPath("logo.png");
        assertTrue(path.endsWith("logo.png"));
    }
}