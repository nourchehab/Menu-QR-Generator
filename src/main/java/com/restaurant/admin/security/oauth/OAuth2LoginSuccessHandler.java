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
                                    Authentication authentication) throws IOException {

    OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
    String email = oauthUser.getAttribute("email");

    log.info("OAuth2 login for email='{}'", email);

    if (email != null) {
        Optional<SimpleUser> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            SimpleUser user = optionalUser.get();

            if (user.isRestaurantSetupComplete()) {
                response.sendRedirect("/restaurants");
            } else {
                response.sendRedirect("/choose-option");
            }
            return;
        }
    }

    // fallback (very unlikely case)
    response.sendRedirect("/choose-option");
}
}
