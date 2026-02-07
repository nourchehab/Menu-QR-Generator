package com.restaurant.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF for now (simpler during development)
                .csrf(csrf -> csrf.disable())
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                // Public pages
                .requestMatchers(
                        "/",
                        "/login",
                        "/signup",
                        "/auth/**",
                        "/css/**",
                        "/js/**",
                        "/images/**"
                ).permitAll()
                // Everything else requires login
                .anyRequest().authenticated()
                )
                // 🔥 THIS IS THE IMPORTANT PART
                .formLogin(form -> form
                .loginPage("/login") // your login.html
                .loginProcessingUrl("/login") // form POST action
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
                )
                .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
