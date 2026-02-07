package com.restaurant.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.restaurant.admin.security.oauth.CustomOAuth2UserService;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login.html",
                    "/otp.html",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/oauth2/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login.html")
                .userInfoEndpoint(user -> user.userService(customOAuth2UserService))
                .defaultSuccessUrl("/", true)
            );

        // IMPORTANT: If you don't need username/password login, keep this disabled.
        // If you DO want form login too, uncomment these lines:
        /*
        http.formLogin(form -> form
            .loginPage("/login.html")
            .permitAll()
        );
        */

        return http.build();
    }
}
