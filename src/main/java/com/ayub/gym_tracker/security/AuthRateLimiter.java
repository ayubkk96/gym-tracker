package com.ayub.gym_tracker.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Persistent fixed windows; atomic across requests, restarts and app instances. */
@Service
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    public AuthRateLimiter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean allow(String bucket, int limit, int seconds) {
        Integer count = jdbc.queryForObject("""
                INSERT INTO auth_rate_limits(bucket_hash, attempts, expires_at)
                VALUES (?, 1, now() + (? * interval '1 second'))
                ON CONFLICT (bucket_hash) DO UPDATE SET
                  attempts = CASE WHEN auth_rate_limits.expires_at <= now() THEN 1
                    ELSE LEAST(auth_rate_limits.attempts + 1, 1000000) END,
                  expires_at = CASE WHEN auth_rate_limits.expires_at <= now()
                    THEN EXCLUDED.expires_at ELSE auth_rate_limits.expires_at END
                RETURNING attempts
                """, Integer.class, hash(bucket), seconds);
        return count != null && count <= limit;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    @Scheduled(fixedDelay = 900000)
    public void purgeExpired() {
        jdbc.update("DELETE FROM auth_rate_limits WHERE expires_at < now()");
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at < now()");
    }
}
