package com.restaurant.admin.security.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.repository.SimpleUserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private SimpleUserRepository userRepository;

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // Get the OAuth2 user
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");

        // Debug log: show incoming principal email
        log.info("OAuth2 login for email='{}' principalName='{}'", email, authentication.getName());

        // Check if user has completed restaurant setup
        if (email != null) {
            Optional<SimpleUser> user = userRepository.findByEmail(email);

            if (user.isPresent()) {
                SimpleUser u = user.get();
                log.info("Found user id={} email={} googleLinked={} googleSub={} restaurantSetupComplete={}",
                        u.getId(), u.getEmail(), u.isGoogleLinked(), u.getGoogleSub(), u.isRestaurantSetupComplete());

                if (!u.isRestaurantSetupComplete()) {
                    // New OAuth user: redirect to restaurant setup
                    response.sendRedirect("/choose-option");
                    return;
                }
            } else {
                log.info("No user found for email='{}'. Will redirect to default dashboard.", email);
            }
        }
        
        // Existing user or setup complete: redirect to dashboard
        response.sendRedirect("/dashboard");
    }
}
