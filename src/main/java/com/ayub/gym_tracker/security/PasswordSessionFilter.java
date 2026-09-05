package com.ayub.gym_tracker.security;

import com.ayub.gym_tracker.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class PasswordSessionFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    public PasswordSessionFilter(AppUserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TrackerPrincipal principal) {
            boolean current = users.findById(principal.userId())
                    .map(user -> user.getPasswordVersion() == principal.passwordVersion()).orElse(false);
            if (!current) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please sign in again.\"}");
                } else {
                    response.sendRedirect("/login.html");
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
