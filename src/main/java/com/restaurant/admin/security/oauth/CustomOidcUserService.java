package com.restaurant.admin.security.oauth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final SimpleUserRepository simpleUserRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOidcUserService(SimpleUserRepository simpleUserRepository,
                                 PasswordEncoder passwordEncoder) {
        this.simpleUserRepository = simpleUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email != null && !email.isBlank()) {
            Optional<SimpleUser> existing = simpleUserRepository.findByEmail(email);

            if (existing.isEmpty()) {
                SimpleUser u = new SimpleUser();
                u.setEmail(email);

                // store a valid hashed password (user won't use it)
                u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                u.setRestaurantSetupComplete(false);  // New OAuth users need setup

                simpleUserRepository.save(u);
            }
        }

        return oidcUser;
    }
}
