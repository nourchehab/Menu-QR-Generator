package com.restaurant.admin.security;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.RestaurantRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthRedirectService {

    private final RestaurantRepository restaurantRepository;

    public AuthRedirectService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public boolean needsRestaurantSetup(SimpleUser user) {
        if (user == null) {
            return true;
        }
        return !user.isRestaurantSetupComplete() || !restaurantRepository.existsByUser(user);
    }

    public String resolvePostLoginRedirect(SimpleUser user) {
        return needsRestaurantSetup(user) ? "/details" : "/restaurants";
    }
}
