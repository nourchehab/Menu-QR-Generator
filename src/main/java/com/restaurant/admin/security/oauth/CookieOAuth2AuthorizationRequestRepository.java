package com.restaurant.admin.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

/**
 * Legacy cookie-based implementation: serialize the OAuth2AuthorizationRequest into the cookie value
 * (Base64 of Java-serialized bytes). This mirrors the original approach used before the token-cache
 * was introduced. Note: this can produce large cookies and may fail when the cookie is too big.
 */
public class CookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    public static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    private static final int COOKIE_EXPIRATION_SECONDS = 300;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                String val = c.getValue();
                if (val == null || val.isBlank()) return null;
                try {
                    byte[] bytes = Base64.getUrlDecoder().decode(val);
                    Object obj = SerializationUtils.deserialize(bytes);
                    if (obj instanceof OAuth2AuthorizationRequest) {
                        return (OAuth2AuthorizationRequest) obj;
                    }
                } catch (Exception ex) {
                    log.debug("Failed to deserialize OAuth2AuthorizationRequest from cookie: {}", ex.toString());
                }
            }
        }
        return null;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeCookie(response, request != null && request.isSecure());
            return;
        }

        try {
            byte[] bytes = SerializationUtils.serialize(authorizationRequest);
            String base64 = Base64.getUrlEncoder().encodeToString(bytes);

            StringBuilder sb = new StringBuilder();
            sb.append(COOKIE_NAME).append("=").append(base64)
              .append("; Path=/")
              .append("; Max-Age=").append(COOKIE_EXPIRATION_SECONDS)
              .append("; HttpOnly");

            boolean forwardedHttps = request != null && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            boolean forceSecure = "true".equalsIgnoreCase(System.getenv("FORCE_COOKIE_SECURE"));
            boolean secure = (request != null && request.isSecure()) || forwardedHttps || forceSecure;
            if (secure) sb.append("; Secure");

            sb.append("; SameSite=None");
            response.addHeader("Set-Cookie", sb.toString());
        } catch (Exception ex) {
            log.warn("Failed to serialize OAuth2AuthorizationRequest to cookie: {}", ex.toString());
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
        removeCookie(response, request != null && request.isSecure());
        return req;
    }

    private void removeCookie(HttpServletResponse response, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append("=")
          .append("; Path=/")
          .append("; Max-Age=0")
          .append("; HttpOnly");
        if (secure) sb.append("; Secure");
        sb.append("; SameSite=None");
        response.addHeader("Set-Cookie", sb.toString());
    }
}
