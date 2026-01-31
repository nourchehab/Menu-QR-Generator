package com.restaurant.admin.service;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.restaurant.admin.model.User;
import com.restaurant.admin.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public CustomOAuth2SuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            response.sendRedirect("/login?oauth2_error=true");
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setProvider("GOOGLE");
            if ((user.getUsername() == null || user.getUsername().isBlank()) && name != null) {
                user.setUsername(name);
            }
            userRepository.save(user);
        } else {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername(name);
            newUser.setProvider("GOOGLE");
            newUser.setRole("ADMIN");
            newUser.setIsActive(true);
            newUser.setPassword(null);
            userRepository.save(newUser);
        }

        response.sendRedirect("/");
    }
}
