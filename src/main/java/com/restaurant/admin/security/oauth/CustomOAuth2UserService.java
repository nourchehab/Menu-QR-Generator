package com.restaurant.admin.security.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        Map<String, Object> attrs = new HashMap<>(oauth2User.getAttributes());
        Set<GrantedAuthority> authorities = (Set<GrantedAuthority>) oauth2User.getAuthorities();

        // Google typically provides "email"
        Object emailObj = attrs.get("email");
        if (emailObj == null || String.valueOf(emailObj).isBlank()) {
            // Fallbacks if provider differs
            emailObj = attrs.get("preferred_username");
        }

        // IMPORTANT: force the "name" attribute key to be "email"
        // so principal.getName() == email everywhere in your app
        if (emailObj != null && !String.valueOf(emailObj).isBlank()) {
            attrs.put("email", String.valueOf(emailObj));
        }

        return new DefaultOAuth2User(authorities, attrs, "email");
    }
}