package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.security.AuthRateLimiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    // Internal mail payload only. Never return it from a controller or log it.
    public record Delivery(String email, String token) {
        @Override public String toString() { return "Delivery[redacted]"; }
    }

    @Transactional
    public Optional<Delivery> issue(String email) {
        var users = jdbc.query("SELECT id, email FROM app_users WHERE lower(email) = lower(?) FOR UPDATE",
                (rs, row) -> new Object[]{rs.getLong("id"), rs.getString("email")}, email);
        if (users.isEmpty()) return Optional.empty();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                INSERT INTO password_reset_tokens(user_id, token_hash, expires_at)
                VALUES (?, ?, now() + interval '15 minutes')
                ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash,
                  expires_at = EXCLUDED.expires_at
                """, users.getFirst()[0], AuthRateLimiter.hash(token));
        return Optional.of(new Delivery((String) users.getFirst()[1], token));
    }

    @Transactional
    public boolean confirm(String token, String password) {
        String hash = AuthRateLimiter.hash(token);
        var ids = jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash = ?",
                Long.class, hash);
        if (ids.isEmpty()) return false;
        Long userId = ids.getFirst();
        // Same lock order as issue(): user first, then token. Only one consumer wins.
        jdbc.queryForList("SELECT id FROM app_users WHERE id = ? FOR UPDATE", Long.class, userId);
        int consumed = jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ? AND token_hash = ? AND expires_at > now()",
                userId, hash);
        if (consumed == 0) return false;
        jdbc.update("UPDATE app_users SET password_hash = ?, password_version = password_version + 1 WHERE id = ?",
                encoder.encode(password), userId);
        return true;
    }
}
