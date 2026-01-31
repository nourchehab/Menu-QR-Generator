package com.restaurant.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.restaurant.admin.service.CustomOAuth2SuccessHandler;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2SuccessHandler successHandler;

    public SecurityConfig(CustomOAuth2SuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/oauth2/**", "/login/oauth2/**", "/h2-console/**")
                .permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .formLogin(Customizer.withDefaults())
            .oauth2Login(oauth2 -> oauth2
                .successHandler(successHandler)
                .failureUrl("/login?oauth2_error=true")
            )
            .logout(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withUsername("admin")
                .password("admin123")
                .roles("ADMIN")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
