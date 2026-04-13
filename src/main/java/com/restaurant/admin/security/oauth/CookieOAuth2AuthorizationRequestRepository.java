package com.restaurant.admin.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple server-side cache approach:
 * - Store a short UUID token in a cookie (small, under typical limits)
 * - Keep the full OAuth2AuthorizationRequest in an in-memory map keyed by the token
 * This avoids oversized cookies while keeping the request short-lived and retrievable
 * across redirects (helpful when sessions are lost).
 */
public class CookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);

    public static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    private static final int COOKIE_EXPIRATION_SECONDS = 300; // 5 minutes

    private static final Map<String, CachedEntry> CACHE = new ConcurrentHashMap<>();

    private static class CachedEntry {
        final OAuth2AuthorizationRequest request;
        final long createdAt;

        CachedEntry(OAuth2AuthorizationRequest request) {
            this.request = request;
            this.createdAt = System.currentTimeMillis();
        }
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                String token = c.getValue();
                if (token == null || token.isBlank()) return null;
                CachedEntry entry = CACHE.get(token);
                if (entry == null) return null;
                // Validate TTL
                long age = (System.currentTimeMillis() - entry.createdAt) / 1000L;
                if (age > COOKIE_EXPIRATION_SECONDS) {
                    CACHE.remove(token);
                    return null;
                }
                return entry.request;
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

        // Generate a short token and store the full request server-side
        String token = UUID.randomUUID().toString();
        CACHE.put(token, new CachedEntry(authorizationRequest));

        log.debug("Saved OAuth2 authorization request in server cache under token {} (ttl={}s)", token, COOKIE_EXPIRATION_SECONDS);

        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append("=").append(token)
          .append("; Path=/")
          .append("; Max-Age=").append(COOKIE_EXPIRATION_SECONDS)
          .append("; HttpOnly");

        // Decide Secure flag: true if request is secure, X-Forwarded-Proto indicates https,
        // or the operator set FORCE_COOKIE_SECURE=true in the environment.
        boolean forwardedHttps = request != null && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        boolean forceSecure = "true".equalsIgnoreCase(System.getenv("FORCE_COOKIE_SECURE"));
        boolean secure = (request != null && request.isSecure()) || forwardedHttps || forceSecure;
        if (secure) sb.append("; Secure");

        log.debug("OAuth cookie save: request.isSecure={} X-Forwarded-Proto={} FORCE_COOKIE_SECURE={} -> Secure={}",
            request != null && request.isSecure(), request != null ? request.getHeader("X-Forwarded-Proto") : null,
            forceSecure, secure);
        sb.append("; SameSite=None");

        response.addHeader("Set-Cookie", sb.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        if (request == null) return null;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                String token = c.getValue();
                if (token == null || token.isBlank()) {
                    removeCookie(response, request.isSecure());
                    return null;
                }
                CachedEntry entry = CACHE.remove(token);
                removeCookie(response, request.isSecure());
                return entry != null ? entry.request : null;
            }
        }
        return null;
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
