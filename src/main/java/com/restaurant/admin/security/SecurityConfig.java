package com.restaurant.admin.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.restaurant.admin.config.CustomAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.logout.LogoutHandler;




@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomAuthenticationProvider authenticationProvider;

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        .csrf(csrf -> csrf.disable())
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/signup", "/auth/signup", "/auth/**", "/choose-option", "/details", "/otp.html", "/reset-password.html", "/css/**", "/js/**", "/images/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key("restaurant-admin-remember-me-2026-long-random-secret")
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
                .tokenValiditySeconds(60 * 60 * 24 * 30)
                .userDetailsService(userDetailsService)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(clearRememberMeCookieLogoutHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public LogoutHandler clearRememberMeCookieLogoutHandler() {
        return new LogoutHandler() {
            @Override
            public void logout(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.Authentication authentication) {

                Cookie cookie = new Cookie("remember-me", "");
                cookie.setPath("/");          // IMPORTANT: must match the original cookie Path
                cookie.setMaxAge(0);          // delete immediately
                cookie.setHttpOnly(true);     // safe default
                response.addCookie(cookie);
            }
        };
    }

}