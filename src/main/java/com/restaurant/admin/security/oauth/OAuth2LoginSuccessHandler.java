package com.restaurant.admin.security.oauth;

import java.io.IOException;

import com.restaurant.admin.model.SimpleUser;
import com.restaurant.admin.security.AuthRedirectService;
import com.restaurant.admin.service.SimpleUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final SimpleUserService userService;
    private final AuthRedirectService authRedirectService;

    public OAuth2LoginSuccessHandler(SimpleUserService userService,
                                     AuthRedirectService authRedirectService) {
        this.userService = userService;
        this.authRedirectService = authRedirectService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");

        log.info("OAuth2 login for email='{}'", email);

        SimpleUser user = userService.findByEmail(email);
        if (user == null) {
            log.warn("OAuth2 login succeeded but no local user record was found for email='{}'", email);
            response.sendRedirect("/login?oauthError=true");
            return;
        }

        response.sendRedirect(authRedirectService.resolvePostLoginRedirect(user));
    }
}
