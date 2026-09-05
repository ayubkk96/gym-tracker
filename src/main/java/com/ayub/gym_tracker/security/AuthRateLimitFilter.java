package com.ayub.gym_tracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Locale;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int REGISTRATION_CLIENT_LIMIT = 10;
    private static final int REGISTRATION_GLOBAL_LIMIT = 200;
    private static final int REGISTRATION_WINDOW_SECONDS = 3600;

    private final AuthRateLimiter limiter;
    public AuthRateLimitFilter(AuthRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getServletPath();
            if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
            int limit = switch (path) {
                case "/api/auth/login" -> 100;
                case "/api/users" -> REGISTRATION_GLOBAL_LIMIT;
                case "/api/auth/password-reset/request" -> 30;
                case "/api/auth/password-reset/confirm" -> 60;
                default -> 0;
            };
            if (limit != 0) {
                int window = path.equals("/api/users") ? REGISTRATION_WINDOW_SECONDS : 900;
                try {
                    boolean allowed = limiter.allow("global:" + path, limit, window);
                    if (allowed && path.equals("/api/users")) {
                        allowed = limiter.allow("registration:" + clientAddress(request),
                                REGISTRATION_CLIENT_LIMIT, window);
                    }
                    if (allowed && path.equals("/api/auth/login")) {
                        String username = request.getParameter("username");
                        allowed = limiter.allow("login:" + (username == null ? "" :
                                username.trim().toLowerCase(Locale.ROOT)), 10, window);
                    }
                    if (!allowed) {
                        response.setStatus(429);
                        response.setHeader("Retry-After", String.valueOf(window));
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Too many attempts. Please try again later.\"}");
                        return;
                    }
                } catch (DataAccessException exception) {
                    response.setStatus(503); // Fail closed, without database details.
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Sign-in is temporarily unavailable.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        // Render sets X-Forwarded-For for proxied requests. Use the left-most address,
        // which represents the original client, and fall back for local/test traffic.
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (!first.isEmpty()) return first;
        }
        return request.getRemoteAddr();
    }
}
