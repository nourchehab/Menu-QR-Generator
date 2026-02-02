package com.restaurant.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable CSRF completely for API
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/static/**").permitAll()  // Allow static content
                .requestMatchers("/users/**").permitAll()  // Allow all /users endpoints
                .requestMatchers("/diagnostic/**").permitAll()  // Allow diagnostic endpoints
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().permitAll()  // Allow all other requests
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .formLogin(form -> form.disable())  // CRITICAL: Disable form login!
            .httpBasic(basic -> basic.disable());  // Disable basic auth too

        return http.build();
    }
}
