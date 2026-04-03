package com.restaurant.admin.config;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.security.AuthRedirectService;
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
    private final AuthRedirectService authRedirectService;

    public CustomLoginSuccessHandler(SimpleUserService userService,
                                     AuthRedirectService authRedirectService) {
        this.userService = userService;
        this.authRedirectService = authRedirectService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        SimpleUser user = userService.findByEmail(authentication.getName());

        if (user == null) {
            response.sendRedirect("/login?error=true");
            return;
        }

        response.sendRedirect(authRedirectService.resolvePostLoginRedirect(user));
    }
}
