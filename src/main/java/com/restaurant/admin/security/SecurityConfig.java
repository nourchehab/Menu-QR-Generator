package com.restaurant.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Allow everyone to access the H2 console path
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            // 2. H2 Console uses iframes; Spring Security blocks them by default
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            // 3. Disable CSRF protection for the console (it doesn't work with H2)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            // 4. Keep the standard login for everything else
            .formLogin(Customizer.withDefaults());

        return http.build();
    }
}
