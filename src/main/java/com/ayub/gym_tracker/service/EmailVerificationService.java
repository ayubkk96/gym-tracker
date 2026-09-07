package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.security.AuthRateLimiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class EmailVerificationService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Internal mail payload only. Never return or log the raw token.
    public record Delivery(String email, String token) {
        @Override public String toString() { return "Delivery[redacted]"; }
    }

    @Transactional
    public Optional<Delivery> issue(String email) {
        var users = jdbc.query("""
                SELECT id, email
                FROM app_users
                WHERE lower(email) = lower(?) AND email_verified = false
                FOR UPDATE
                """,
                (rs, row) -> new Object[]{rs.getLong("id"), rs.getString("email")},
                email);
        if (users.isEmpty()) return Optional.empty();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                INSERT INTO email_verification_tokens(user_id, token_hash, expires_at)
                VALUES (?, ?, now() + interval '24 hours')
                ON CONFLICT (user_id) DO UPDATE SET
                  token_hash = EXCLUDED.token_hash,
                  expires_at = EXCLUDED.expires_at
                """, users.getFirst()[0], AuthRateLimiter.hash(token));

        return Optional.of(new Delivery((String) users.getFirst()[1], token));
    }

    @Transactional
    public boolean confirm(String token) {
        String hash = AuthRateLimiter.hash(token);
        var ids = jdbc.queryForList(
                "SELECT user_id FROM email_verification_tokens WHERE token_hash = ?",
                Long.class,
                hash
        );
        if (ids.isEmpty()) return false;

        Long userId = ids.getFirst();
        jdbc.queryForList("SELECT id FROM app_users WHERE id = ? FOR UPDATE", Long.class, userId);
        int consumed = jdbc.update("""
                DELETE FROM email_verification_tokens
                WHERE user_id = ? AND token_hash = ? AND expires_at > now()
                """, userId, hash);
        if (consumed == 0) return false;

        jdbc.update("UPDATE app_users SET email_verified = true WHERE id = ?", userId);
        return true;
    }
}
