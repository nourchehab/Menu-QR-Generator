package com.restaurant.admin.config;

import com.restaurant.admin.service.SimpleUserService;
import com.restaurant.admin.service.LoginResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    private SimpleUserService userService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();

        LoginResult result = userService.authenticateUser(email, password);

        switch (result) {
            case SUCCESS:
                return new UsernamePasswordAuthenticationToken(
                    email, 
                    password, 
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            
            case EMAIL_NOT_FOUND:
                throw new BadCredentialsException("No account found with this email");
            
            case WRONG_PASSWORD:
                throw new BadCredentialsException("Incorrect password");
            
            default:
                throw new BadCredentialsException("Authentication failed");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}