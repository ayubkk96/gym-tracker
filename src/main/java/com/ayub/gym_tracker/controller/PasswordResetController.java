package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.security.AuthRateLimiter;
import com.ayub.gym_tracker.service.PasswordResetService;
import com.ayub.gym_tracker.service.RecoveryMailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService tokens;
    private final RecoveryMailService mail;
    private final AuthRateLimiter limiter;
    public PasswordResetController(PasswordResetService tokens, RecoveryMailService mail, AuthRateLimiter limiter) {
        this.tokens = tokens;
        this.mail = mail;
        this.limiter = limiter;
    }

    public record ResetRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record ResetConfirmation(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                                    @NotBlank @Size(min = 12, max = 64) String password) {
        @Override public String toString() { return "ResetConfirmation[redacted]"; }
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody ResetRequest request) {
        if (!mail.enabled()) return ResponseEntity.status(503).body(Map.of("message", "Password recovery is not available yet. Contact the app owner."));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (limiter.allow("reset:" + email, 3, 900)) {
            try { mail.request(email); }
            catch (TaskRejectedException exception) {
                return ResponseEntity.status(503).body(Map.of("message", "Please try again later."));
            }
        }
        return ResponseEntity.accepted().body(Map.of("message", "If an account matches that email, a reset link will be sent. Check your spam folder too."));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@Valid @RequestBody ResetConfirmation request) {
        if (!tokens.confirm(request.token(), request.password())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This link is invalid or expired. Request a new one."));
        }
        return ResponseEntity.ok(Map.of("message", "Password updated. Sign in again on all your devices."));
    }
}
