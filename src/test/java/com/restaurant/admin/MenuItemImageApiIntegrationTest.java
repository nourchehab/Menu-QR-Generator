package com.restaurant.admin;

import com.restaurant.admin.model.MenuItem;
import com.restaurant.admin.model.Restaurant;
import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.MenuItemRepository;
import com.restaurant.admin.repository.RestaurantRepository;
import com.restaurant.admin.repository.SimpleUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile; 
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MenuItemImageApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleUserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    private MenuItem item;

    @BeforeEach
    void setup() {
        menuItemRepository.deleteAll();
        restaurantRepository.deleteAll();
        userRepository.deleteAll();

        SimpleUser user = new SimpleUser();
        user.setEmail("test@example.com");
        user.setPassword("x");
        user.setRestaurantSetupComplete(true);
        userRepository.save(user);

        Restaurant restaurant = new Restaurant("Test Resto", "cafe", user);
        restaurantRepository.save(restaurant);

        MenuItem m = new MenuItem("Burger", new BigDecimal("10.50"), "Tasty");
        m.setRestaurant(restaurant);
        item = menuItemRepository.save(m);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void uploadAndDeleteImage_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "itemPhoto",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47} // minimal PNG signature bytes
        );

        mockMvc.perform(multipart("/api/items/{id}/image", item.getId())
                        .file(file)
                        .with(user("test@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.startsWith("/uploads/photos/"),
                        org.hamcrest.Matchers.startsWith("http")
                )));

        MenuItem refreshed = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(refreshed.getPhotoPath()).isNotBlank();

        mockMvc.perform(delete("/api/items/{id}/image", item.getId())
                        .with(user("test@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MenuItem afterDelete = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(afterDelete.getPhotoPath()).isNull();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void uploadImage_rejectsInvalidType() throws Exception {
        MockMultipartFile bad = new MockMultipartFile(
                "itemPhoto",
                "not-an-image.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/items/{id}/image", item.getId())
                        .file(bad)
                        .with(user("test@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
