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
    private final AuthRateLimiter limiter;
    public AuthRateLimitFilter(AuthRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getServletPath();
            if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
            // No client-supplied forwarding header is trusted for these budgets.
            int limit = switch (path) {
                case "/api/auth/login" -> 100;
                case "/api/users" -> 10;
                case "/api/auth/password-reset/request" -> 30;
                case "/api/auth/password-reset/confirm" -> 60;
                default -> 0;
            };
            if (limit != 0) {
                int window = path.equals("/api/users") ? 3600 : 900;
                try {
                    boolean allowed = limiter.allow("global:" + path, limit, window);
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
}
