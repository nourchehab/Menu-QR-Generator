package com.restaurant.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.restaurant.admin.security.oauth.CustomOAuth2UserService;
import com.restaurant.admin.security.oauth.CustomOidcUserService;
import com.restaurant.admin.security.oauth.OAuth2LoginFailureHandler;
import com.restaurant.admin.security.oauth.OAuth2LoginSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final CustomAuthenticationProvider customAuthenticationProvider;
    private final CustomLoginSuccessHandler formLoginSuccessHandler;
    private final String activeProfile;

    public SecurityConfig(
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2LoginSuccessHandler successHandler,
            OAuth2LoginFailureHandler failureHandler,
            CustomAuthenticationProvider customAuthenticationProvider,
            CustomLoginSuccessHandler formLoginSuccessHandler,
            @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:}") String activeProfile
    ) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.customAuthenticationProvider = customAuthenticationProvider;
        this.formLoginSuccessHandler = formLoginSuccessHandler;
        this.activeProfile = activeProfile == null ? "" : activeProfile;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(customAuthenticationProvider);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                )
                .authorizeHttpRequests(auth -> {
                    // Allow unauthenticated access to signup endpoints
                    auth.requestMatchers("/signup", "/signup/verify", "/signup/verify/resend").permitAll();
                    // Public assets and selected API endpoints
                    auth.requestMatchers(
                            "/", "/login", "/signup",
                            "/css/**", "/js/**", "/images/**",
                            "/uploads/**",
                            "/m/**",
                            "/api/public/**",
                            "/oauth2/**", "/login/oauth2/**", "/otp.html", "/reset-password.html",
                            "/auth/**", "/menu/**", "/api/public/**", "/api/qr/**", "/api/login/**", "/r/**",
                            "/b/**", "/api/restaurant/branch/**",
                            "/api/public/branch/**"
                    ).permitAll();

                    // During local development allow anonymous access to assistant endpoints
                    if ("local".equalsIgnoreCase(this.activeProfile)) {
                        auth.requestMatchers("/api/classify/chat", "/api/classify/suggest").permitAll();
                    }

                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(formLoginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                );

        return http.build();
    }
}
