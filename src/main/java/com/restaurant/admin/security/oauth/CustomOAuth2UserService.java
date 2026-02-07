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

import com.restaurant.admin.model.User;
import com.restaurant.admin.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HttpSession session;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            HttpSession session
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.session = session;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oauthUser = new DefaultOAuth2UserService().loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name"); // optional, not used right now

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by Google");
        }

        // ✅ Skip OTP for Google login
        session.setAttribute("OTP_VERIFIED", true);

        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            user = new User();
            user.setEmail(email);
            user.setUsername(generateUsername(email));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(user);
        }

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("email", email);

        return new DefaultOAuth2User(
                Collections.singleton(() -> "ROLE_USER"),
                attributes,
                "email"
        );
    }

    private String generateUsername(String email) {
        String base = email.split("@")[0];
        String username = base;
        int i = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + i;
            i++;
        }
        return username;
    }
}
