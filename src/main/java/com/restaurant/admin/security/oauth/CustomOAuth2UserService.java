package com.restaurant.admin.security.oauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;
import static com.restaurant.admin.util.EmailUtil.normalize;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final SimpleUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(SimpleUserRepository userRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oauthUser = new DefaultOAuth2UserService().loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by Google");
        }

        // Normalize email for consistent lookups (same behavior as OIDC path)
        String normalizedEmail = normalize(email);

        // Try to obtain a provider subject/id (Google OIDC uses "sub", some OAuth2 providers use "id")
        String providerId = null;
        Object subAttr = oauthUser.getAttribute("sub");
        if (subAttr != null) providerId = subAttr.toString();
        else {
            Object idAttr = oauthUser.getAttribute("id");
            if (idAttr != null) providerId = idAttr.toString();
        }

        Optional<SimpleUser> optionalUser = userRepository.findByEmail(normalizedEmail);

        if (optionalUser.isEmpty()) {
            // Auto-create account for new Google users
            SimpleUser user = new SimpleUser();
            user.setEmail(normalizedEmail);

            // Random password: user won't use it, but we store a valid hashed value
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setPasswordSet(false);

            // If provider id is available, link it immediately
            if (providerId != null && !providerId.isBlank()) {
                user.setGoogleLinked(true);
                user.setGoogleSub(providerId);
            }

            user.setRestaurantSetupComplete(false);  // New OAuth users need setup
            userRepository.save(user);
        } else {
            // Existing local account found by email: link provider id if present
            SimpleUser user = optionalUser.get();
            if (providerId != null && !providerId.isBlank()) {
                user.setGoogleLinked(true);
                if (user.getGoogleSub() == null || user.getGoogleSub().isBlank()) {
                    user.setGoogleSub(providerId);
                }
                userRepository.save(user);
            }
        }

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("email", normalizedEmail);

        return new DefaultOAuth2User(
                Collections.singleton(() -> "ROLE_USER"),
                attributes,
                "email"
        );
    }
}
