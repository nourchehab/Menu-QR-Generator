package com.restaurant.admin.config;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.service.SimpleUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SimpleUserService userService;

    public CustomLoginSuccessHandler(SimpleUserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String email = authentication.getName();

        SimpleUser user = userService.findByEmail(email);

        if (user != null && user.isRestaurantSetupComplete()) {
            response.sendRedirect("/dashboard");
        } else {
            response.sendRedirect("/choose-option");
        }
    }
}