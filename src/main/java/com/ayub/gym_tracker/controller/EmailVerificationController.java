package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.security.AuthRateLimiter;
import com.ayub.gym_tracker.service.EmailVerificationMailService;
import com.ayub.gym_tracker.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService tokens;
    private final EmailVerificationMailService mail;
    private final AuthRateLimiter limiter;

    public EmailVerificationController(
            EmailVerificationService tokens,
            EmailVerificationMailService mail,
            AuthRateLimiter limiter
    ) {
        this.tokens = tokens;
        this.mail = mail;
        this.limiter = limiter;
    }

    public record VerificationRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record VerificationConfirmation(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token
    ) {
        @Override public String toString() { return "VerificationConfirmation[redacted]"; }
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(
            @Valid @RequestBody VerificationRequest request
    ) {
        if (!mail.enabled()) {
            return ResponseEntity.status(503).body(Map.of(
                    "message", "Email verification is not available yet."
            ));
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (limiter.allow("verify:" + email, 3, 900)) {
            try {
                mail.request(email);
            } catch (TaskRejectedException exception) {
                return ResponseEntity.status(503).body(Map.of(
                        "message", "Please try again later."
                ));
            }
        }

        return ResponseEntity.accepted().body(Map.of(
                "message", "If that account still needs verification, a new link will be sent. Check your spam folder too."
        ));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(
            @Valid @RequestBody VerificationConfirmation request
    ) {
        if (!mail.enabled()) {
            return ResponseEntity.status(503).body(Map.of(
                    "message", "Email verification is not available yet."
            ));
        }
        if (!tokens.confirm(request.token())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "This verification link is invalid or expired. Request a new one."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Email verified. You can now sign in."
        ));
    }
}
