package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.security.AuthRateLimiter;
import com.ayub.gym_tracker.service.AccountDataService;
import com.ayub.gym_tracker.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountDataController {
    public record DeleteAccountRequest(@NotBlank @Size(max = 128) String password,
                                       @NotBlank @Pattern(regexp = "DELETE") String confirmation) {}
    private final AccountDataService data;
    private final CurrentUserService users;
    private final AuthRateLimiter limiter;
    public AccountDataController(AccountDataService data, CurrentUserService users, AuthRateLimiter limiter) {
        this.data = data; this.users = users; this.limiter = limiter;
    }
    @GetMapping("/export")
    public ResponseEntity<?> export() {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Disposition", "attachment; filename=\"gym-tracker-export.json\"")
                .body(data.export());
    }
    @DeleteMapping
    public ResponseEntity<?> delete(@Valid @RequestBody DeleteAccountRequest body,
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (!limiter.allow("delete-account:" + users.getCurrentUser().getId(), 5, 900)) {
            return ResponseEntity.status(429).header("Retry-After", "900").build();
        }
        if (!data.delete(body.password())) return ResponseEntity.status(403).build();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
