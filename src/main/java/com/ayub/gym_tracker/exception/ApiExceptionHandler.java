package com.ayub.gym_tracker.exception;

import com.ayub.gym_tracker.monitoring.RequestMonitoringFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Map.of("message", "Please check the supplied values.",
                "requestId", String.valueOf(request.getAttribute(RequestMonitoringFilter.REQUEST_ID))));
    }
}
