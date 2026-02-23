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

import static com.restaurant.admin.util.EmailUtil.normalize;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final SimpleUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOidcUserService(SimpleUserRepository userRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = normalize(oidcUser.getEmail());
        String sub = oidcUser.getSubject();

        if (email != null && !email.isBlank()) {
            Optional<SimpleUser> existingOpt = userRepository.findByEmail(email);

            if (existingOpt.isEmpty()) {
                SimpleUser u = new SimpleUser();
                u.setEmail(email);

                // random password hash; user hasn't set a local password yet
                u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                u.setPasswordSet(false);

                u.setGoogleLinked(true);
                u.setGoogleSub(sub);

                u.setRestaurantSetupComplete(false);
                userRepository.save(u);

            } else {
                SimpleUser u = existingOpt.get();
                u.setGoogleLinked(true);

                if (u.getGoogleSub() == null || u.getGoogleSub().isBlank()) {
                    u.setGoogleSub(sub);
                }

                userRepository.save(u);
            }
        }

        return oidcUser;
    }
}
