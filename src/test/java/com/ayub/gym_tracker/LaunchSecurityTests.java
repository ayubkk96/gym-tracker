package com.ayub.gym_tracker;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.monitoring.RequestMonitoringFilter;
import com.ayub.gym_tracker.security.AuthRateLimiter;
import com.ayub.gym_tracker.service.PasswordResetService;
import com.ayub.gym_tracker.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class LaunchSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired UserService users;
    @Autowired PasswordResetService resets;
    @Autowired AuthRateLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private MockMvc mvc;
    private static final String PASSWORD = "Original-password-123";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestMonitoringFilter()).apply(springSecurity()).build();
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        users.register(new UserRegistrationRequest(email, "Test user", PASSWORD,
                LocalDate.of(2026, 1, 1), new DailyTargetRequest(2450,
                new BigDecimal("180"), new BigDecimal("275"), new BigDecimal("75"))));
        return email;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", email).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void resetIsHashedSingleUseAndChangesCredentials() throws Exception {
        String email = account();
        String token = resets.issue(email).orElseThrow().token();
        assertEquals(43, token.length());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token));
        assertTrue(resets.confirm(token, "New-private-password-123"));
        assertFalse(resets.confirm(token, "Another-password-123"));
        entityManager.clear();
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        assertNotNull(login(email, "New-private-password-123"));
    }

    @Test
    void resetExpiresAndNewRequestReplacesPreviousToken() {
        String email = account();
        String first = resets.issue(email).orElseThrow().token();
        String second = resets.issue(email).orElseThrow().token();
        assertFalse(resets.confirm(first, "Changed-password-123"));
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                AuthRateLimiter.hash(second));
        assertFalse(resets.confirm(second, "Changed-password-123"));
        assertTrue(resets.issue("missing-" + email).isEmpty());
    }

    @Test
    void oldSessionIsRejectedAfterReset() throws Exception {
        String email = account();
        MockHttpSession session = login(email, PASSWORD);
        String token = resets.issue(email).orElseThrow().token();
        assertTrue(resets.confirm(token, "Changed-password-123"));
        entityManager.clear();
        mvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetEndpointsKeepCsrfAndValidatePasswords() throws Exception {
        mvc.perform(get("/reset-password.html")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"valid@example.test\"}"))
                .andExpect(status().isServiceUnavailable()); // Not configured by default, never fake success.
    }

    @Test
    void loginRateLimitNormalizesAccountAndReturnsRetryAfter() throws Exception {
        String email = "missing-" + UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", " " + email.toUpperCase() + " ")
                        .param("password", "wrong").header("X-Forwarded-For", "198.51.100.5"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void registrationHasAnIndependentBudgetAndCountersExpire() throws Exception {
        for (int i = 0; i < 10; i++) assertTrue(limiter.allow("global:/api/users", 10, 3600));
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
        String bucket = UUID.randomUUID().toString();
        assertTrue(limiter.allow(bucket, 1, 60));
        assertFalse(limiter.allow(bucket, 1, 60));
        jdbc.update("UPDATE auth_rate_limits SET expires_at = now() - interval '1 minute' WHERE bucket_hash = ?",
                AuthRateLimiter.hash(bucket));
        assertTrue(limiter.allow(bucket, 1, 60));
    }

    @Test
    void twoAccountsCannotReadOrOverwriteEachOthersNutritionAndWorkouts() throws Exception {
        String alice = account();
        String bob = account();
        MockHttpSession aliceSession = login(alice, PASSWORD);
        MockHttpSession bobSession = login(bob, PASSWORD);
        String nutrition = "{\"date\":\"2026-09-01\",\"calories\":2100,\"proteinG\":160,\"carbsG\":240,\"fatG\":70}";
        String workout = "{\"date\":\"2026-09-01\",\"workout\":\"Chest\",\"exercises\":[{\"name\":\"Bench Press\",\"sets\":[{\"weightKg\":90,\"reps\":8}]}]}";
        mvc.perform(post("/api/nutrition").session(aliceSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(nutrition))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/workouts").session(aliceSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(workout))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/dashboard").session(bobSession).param("date", "2026-09-01").param("email", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nutrition").isEmpty())
                .andExpect(jsonPath("$.workouts").isEmpty());
        mvc.perform(post("/api/nutrition").session(bobSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(nutrition.replace("2100", "1800"))).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/workouts").session(bobSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(workout.replace("90", "50"))).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/dashboard").session(aliceSession).param("date", "2026-09-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nutrition.calories").value(2100))
                .andExpect(jsonPath("$.workouts[0].exercises[0].sets[0].weightKg").value(90));
        mvc.perform(get("/api/dashboard").session(bobSession).param("date", "2026-09-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nutrition.calories").value(1800))
                .andExpect(jsonPath("$.workouts[0].exercises[0].sets[0].weightKg").value(50));
    }
}
