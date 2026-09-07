package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class EmailVerificationServiceTest {
    @Autowired UserService users;
    @Autowired EmailVerificationService verification;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuesHashedSingleUseTokenAndVerifiesPendingAccount() {
        String email = UUID.randomUUID() + "@example.test";
        users.register(new UserRegistrationRequest(
                email,
                "Pending User",
                "Test-password-123",
                new DailyTargetRequest(
                        2450,
                        new BigDecimal("180"),
                        new BigDecimal("275"),
                        new BigDecimal("75")
                )
        ), true);

        assertFalse(jdbc.queryForObject(
                "SELECT email_verified FROM app_users WHERE email = ?",
                Boolean.class,
                email
        ));

        EmailVerificationService.Delivery delivery = verification.issue(email).orElseThrow();
        String storedHash = jdbc.queryForObject(
                """
                SELECT token_hash
                FROM email_verification_tokens t
                JOIN app_users u ON u.id = t.user_id
                WHERE u.email = ?
                """,
                String.class,
                email
        );

        assertNotEquals(delivery.token(), storedHash);
        assertTrue(verification.confirm(delivery.token()));
        assertTrue(jdbc.queryForObject(
                "SELECT email_verified FROM app_users WHERE email = ?",
                Boolean.class,
                email
        ));
        assertFalse(verification.confirm(delivery.token()));
    }
}
