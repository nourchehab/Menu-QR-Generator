package com.restaurant.admin.security.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // Get the OAuth2 user
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        
        // Check if user has completed restaurant setup
        if (email != null) {
            Optional<SimpleUser> user = userRepository.findByEmail(email);
            
            if (user.isPresent() && !user.get().isRestaurantSetupComplete()) {
                // New OAuth user: redirect to restaurant setup
                response.sendRedirect("/choose-option");
                return;
            }
        }
        
        // Existing user or setup complete: redirect to dashboard
        response.sendRedirect("/dashboard");
    }
}
