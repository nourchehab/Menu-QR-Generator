package com.restaurant.admin.security.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        Map<String, Object> attrs = new HashMap<>(oidcUser.getAttributes());
        Set<GrantedAuthority> authorities = (Set<GrantedAuthority>) oidcUser.getAuthorities();

        // Google OIDC provides "email" if you requested the right scopes
        Object emailObj = attrs.get("email");
        if (emailObj != null && !String.valueOf(emailObj).isBlank()) {
            attrs.put("email", String.valueOf(emailObj));
        }

        // IMPORTANT: force the "name" attribute key to be "email"
        // so principal.getName() == email everywhere in your app
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
    }
}