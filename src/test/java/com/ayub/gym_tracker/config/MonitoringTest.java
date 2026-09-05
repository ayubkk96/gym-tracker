package com.ayub.gym_tracker.config;

import com.ayub.gym_tracker.controller.HealthController;
import com.ayub.gym_tracker.monitoring.RequestMonitoringFilter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringTest {
    @Test
    void unexpectedFailureIsRedactedAndHasAServerGeneratedReference() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.addHeader("X-Request-ID", "attacker-controlled");
        var response = new MockHttpServletResponse();
        new RequestMonitoringFilter().doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("secret-password=user@example.test");
        });
        assertEquals(500, response.getStatus());
        assertFalse(response.getContentAsString().contains("secret-password"));
        assertFalse(response.getContentAsString().contains("user@example.test"));
        assertNotEquals("attacker-controlled", response.getHeader("X-Request-ID"));
        assertTrue(response.getContentAsString().contains(response.getHeader("X-Request-ID")));
    }

    @Test
    void readinessFailsWithoutLeakingDatabaseDetails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("secret database address"));
        var response = new HealthController(jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().get("status"));
        assertFalse(response.getBody().toString().contains("secret"));
    }
}
