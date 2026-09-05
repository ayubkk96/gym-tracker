package com.ayub.gym_tracker.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {

    @Test
    void registrationUsesClientSpecificBucketBehindRenderProxy() throws Exception {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = registrationRequest("203.0.113.10");
        filter.doFilter(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = registrationRequest("203.0.113.11");
        filter.doFilter(second, new MockHttpServletResponse(), chain);

        verify(limiter, times(2)).allow("global:/api/users", 200, 3600);
        verify(limiter).allow("registration:203.0.113.10", 10, 3600);
        verify(limiter).allow("registration:203.0.113.11", 10, 3600);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void registrationReturns429WhenOnlyThatClientExceedsBudget() throws Exception {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        when(limiter.allow("global:/api/users", 200, 3600)).thenReturn(true);
        when(limiter.allow("registration:203.0.113.20", 10, 3600)).thenReturn(false);
        when(limiter.allow("registration:203.0.113.21", 10, 3600)).thenReturn(true);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(registrationRequest("203.0.113.20"), blocked, chain);
        assertEquals(429, blocked.getStatus());
        assertEquals("3600", blocked.getHeader("Retry-After"));

        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilter(registrationRequest("203.0.113.21"), allowed, chain);
        assertEquals(200, allowed.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void cloudflareClientAddressTakesPrecedence() throws Exception {
        AuthRateLimiter limiter = mock(AuthRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter);

        MockHttpServletRequest request = registrationRequest("198.51.100.9");
        request.addHeader("CF-Connecting-IP", "203.0.113.30");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(limiter).allow("registration:203.0.113.30", 10, 3600);
    }

    private MockHttpServletRequest registrationRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
        request.setServletPath("/api/users");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
