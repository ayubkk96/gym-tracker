package com.ayub.gym_tracker.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestMonitoringFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestMonitoringFilter.class);
    public static final String REQUEST_ID = "tracker.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString(); // Never echo an untrusted incoming ID.
        request.setAttribute(REQUEST_ID, id);
        response.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        String errorType = "none";
        try {
            chain.doFilter(request, response);
        } catch (Exception exception) {
            errorType = exception.getClass().getSimpleName();
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.setStatus(500);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Something went wrong. Please try again.\",\"requestId\":\"" + id + "\"}");
            }
        } finally {
            if (response.getStatus() >= 500 || !errorType.equals("none")) {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                // Route template only. No query strings, bodies, emails, tokens or exception messages.
                log.error("event=http_server_error requestId={} status={} route={} exceptionType={} durationMs={}",
                        id, response.getStatus(), pattern == null ? "unmatched" : pattern,
                        errorType, (System.nanoTime() - start) / 1_000_000);
            } else if (response.getStatus() == 429) {
                log.warn("event=auth_rate_limited requestId={}", id);
            }
        }
    }
}
