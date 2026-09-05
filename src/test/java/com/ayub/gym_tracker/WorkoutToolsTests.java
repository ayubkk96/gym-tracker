package com.ayub.gym_tracker;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.service.UserService;
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
class WorkoutToolsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    static final String TEMPLATE = """
            {"name":"Chest","exercises":[{"name":"Bench","setCount":6,"notes":"Pause"}]}
            """;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    MockHttpSession account() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        users.register(new UserRegistrationRequest(email, "Tester", "Test-password-123",
                LocalDate.of(2026, 1, 1), new DailyTargetRequest(2450,
                new BigDecimal("180"), new BigDecimal("275"), new BigDecimal("75"))));
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", email).param("password", "Test-password-123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test void earlierDatesCanBeViewedCreatedAndEditedWithoutRenumbering() throws Exception {
        var session = account(); // Tracking starts 2026-01-01.
        long userId = jdbc.queryForObject("SELECT max(id) FROM app_users", Long.class);
        jdbc.update("INSERT INTO daily_targets(user_id,effective_from,calories,protein_g,carbs_g,fat_g) VALUES (?, '2026-02-01', 2200, 160, 250, 70)", userId);
        mvc.perform(get("/api/dashboard").session(session).param("date","2025-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2025-12-31"))
                .andExpect(jsonPath("$.targets.calories").value(2450))
                .andExpect(jsonPath("$.nutrition").isEmpty()).andExpect(jsonPath("$.workouts").isEmpty());
        String nutrition="{\"date\":\"2025-12-31\",\"calories\":2000,\"proteinG\":150,\"carbsG\":200,\"fatG\":70}";
        for (int calories : new int[]{2000,2100}) {
            mvc.perform(post("/api/nutrition").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(nutrition.replace("2000",String.valueOf(calories)))).andExpect(status().is2xxSuccessful());
        }
        workout(session,"2025-12-31",8);
        workout(session,"2025-12-31",10);
        workout(session,"2026-01-01",12);
        entityManager.flush(); entityManager.clear();
        mvc.perform(get("/api/dashboard").session(session).param("date","2025-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nutrition.calories").value(2100))
                .andExpect(jsonPath("$.nutrition.day").isEmpty())
                .andExpect(jsonPath("$.workouts.length()").value(1))
                .andExpect(jsonPath("$.workouts[0].day").isEmpty())
                .andExpect(jsonPath("$.workouts[0].exercises[0].sets[0].reps").value(10));
        mvc.perform(get("/api/dashboard").session(session).param("date","2026-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workouts[0].day").value(1))
                .andExpect(jsonPath("$.targets.calories").value(2450));
        mvc.perform(get("/api/dashboard").session(session).param("date","2026-02-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targets.calories").value(2200));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM nutrition_logs WHERE user_id=? AND log_date='2025-12-31'",Integer.class,userId));
        assertEquals(java.sql.Date.valueOf("2026-01-01"),jdbc.queryForObject("SELECT min(effective_from) FROM daily_targets WHERE user_id=?",java.sql.Date.class,userId));
    }

    @Test void exportAndDeletionAreScopedAndCascadeAllOwnedData() throws Exception {
        var alice = account();
        long aliceId = jdbc.queryForObject("SELECT max(id) FROM app_users", Long.class);
        String email = jdbc.queryForObject("SELECT email FROM app_users WHERE id = ?", String.class, aliceId);
        var secondSession = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", email).param("password", "Test-password-123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        var bob = account();
        workout(alice, "2026-09-01", 8);
        workout(bob, "2026-09-01", 99);
        mvc.perform(post("/api/workout-templates").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TEMPLATE)).andExpect(status().isOk());
        mvc.perform(post("/api/nutrition").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-01\",\"calories\":2000,\"proteinG\":150,\"carbsG\":200,\"fatG\":70}"))
                .andExpect(status().is2xxSuccessful());
        jdbc.update("INSERT INTO password_reset_tokens(user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '1 hour')", aliceId, UUID.randomUUID().toString());
        var export = mvc.perform(get("/api/account/export").session(alice)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"gym-tracker-export.json\""))
                .andExpect(jsonPath("$.account.id").value(aliceId))
                .andExpect(jsonPath("$.targets.length()").value(1))
                .andExpect(jsonPath("$.nutrition.length()").value(1))
                .andExpect(jsonPath("$.workouts.length()").value(1))
                .andExpect(jsonPath("$.sets[0].reps").value(8))
                .andExpect(jsonPath("$.sets[0].weight_kg").isEmpty())
                .andExpect(jsonPath("$.templates.length()").value(1))
                .andExpect(jsonPath("$.templateExercises[0].set_count").value(6))
                .andReturn().getResponse().getContentAsString();
        assertFalse(export.contains("password")); assertFalse(export.contains("token_hash"));
        long exerciseId = jdbc.queryForObject("SELECT e.id FROM exercise_logs e JOIN workout_sessions w ON w.id=e.workout_session_id WHERE w.user_id=?", Long.class, aliceId);
        long templateId = jdbc.queryForObject("SELECT id FROM workout_templates WHERE user_id=?", Long.class, aliceId);
        mvc.perform(delete("/api/account").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Test-password-123\",\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());
        assertTrue(alice.isInvalid());
        entityManager.clear();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM app_users WHERE id=?",Integer.class,aliceId));
        for (String table : new String[]{"daily_targets","nutrition_logs","workout_sessions","workout_templates","password_reset_tokens"})
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?",Integer.class,aliceId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM exercise_sets WHERE exercise_log_id=?",Integer.class,exerciseId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM workout_template_exercises WHERE template_id=?",Integer.class,templateId));
        mvc.perform(get("/api/account/export").session(secondSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/export").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.sets[0].reps").value(99));
    }

    @Test void deletionRequiresCsrfConfirmationPasswordAndRateLimit() throws Exception {
        mvc.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/account").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        var session=account();
        String correct="{\"password\":\"Test-password-123\",\"confirmation\":\"DELETE\"}";
        mvc.perform(delete("/api/account").session(session).contentType(MediaType.APPLICATION_JSON).content(correct)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/account").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(correct.replace("DELETE","NO")))
                .andExpect(status().isBadRequest());
        for(int i=0;i<5;i++) mvc.perform(delete("/api/account").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(correct.replace("Test-password-123","wrong")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/account").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(correct))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        mvc.perform(get("/api/account/export").session(session)).andExpect(status().isOk());
    }

    @Test void progressSeparatesBodyweightAndWeightedRecordsAndScopesHistory() throws Exception {
        var alice = account();
        var bob = account();
        workout(alice, "2026-09-01", 8);
        workout(bob, "2026-09-02", 99);
        workout(alice, "2026-09-06", 100);
        for (String date : new String[]{"2026-01-01", "2026-09-03"}) {
            mvc.perform(post("/api/workouts").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"date":"%s","workout":"Weighted","exercises":[{"name":" Pull-ups ","sets":[{"weightKg":20,"reps":5}]}]}
                            """.formatted(date))).andExpect(status().is2xxSuccessful());
        }
        mvc.perform(post("/api/nutrition").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-05\",\"calories\":2000,\"proteinG\":150,\"carbsG\":200,\"fatG\":70,\"weightKg\":93}"))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/progress").session(alice).param("through", "2026-09-05").param("days", "7").param("exercise", " PULL-UPS "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.from").value("2026-08-30"))
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.bodyweight[0].value").value(93))
                .andExpect(jsonPath("$.heaviestSets.length()").value(1))
                .andExpect(jsonPath("$.heaviestSets[0].value").value(20))
                .andExpect(jsonPath("$.bodyweightReps.length()").value(1))
                .andExpect(jsonPath("$.bestBodyweight.reps").value(8))
                .andExpect(jsonPath("$.heaviest.date").value("2026-01-01"));
        mvc.perform(get("/api/progress").session(bob).param("through", "2026-09-05").param("exercise", "Pull-ups"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bodyweight").isEmpty())
                .andExpect(jsonPath("$.heaviest").isEmpty()).andExpect(jsonPath("$.bestBodyweight.reps").value(99));
    }

    @Test void progressRequiresAuthenticationAndValidRangeAndHandlesEmptyHistory() throws Exception {
        mvc.perform(get("/api/progress").param("through", "2026-09-05")).andExpect(status().isUnauthorized());
        var session = account();
        for (String days : new String[]{"0", "366"}) {
            mvc.perform(get("/api/progress").session(session).param("through", "2026-09-05").param("days", days))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/progress").session(session).param("through", "bad-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/progress").session(session).param("through", "2026-09-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exercises").isEmpty())
                .andExpect(jsonPath("$.bodyweight").isEmpty()).andExpect(jsonPath("$.heaviest").isEmpty());
    }

    @Test void templatesArePrivateUpsertedAndDoNotLogWorkouts() throws Exception {
        var alice = account();
        var bob = account();
        int before = jdbc.queryForObject("SELECT count(*) FROM workout_sessions", Integer.class);
        mvc.perform(post("/api/workout-templates").session(alice).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(TEMPLATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exercises[0].setCount").value(6));
        mvc.perform(post("/api/workout-templates").session(alice).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(TEMPLATE.replace("Chest", " chest ").replace(":6", ":20")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/workout-templates").session(alice)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].exercises[0].setCount").value(20));
        mvc.perform(get("/api/workout-templates").session(bob)).andExpect(jsonPath("$.length()").value(0));
        long id = jdbc.queryForObject("SELECT id FROM workout_templates WHERE name = 'chest'", Long.class);
        mvc.perform(delete("/api/workout-templates/" + id).session(bob).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workout-templates/" + id).session(alice).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_template_exercises WHERE template_id = ?", Integer.class, id));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM workout_sessions", Integer.class));
    }

    @Test void templatesValidateNestedInputAndRequireAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/workout-templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workouts/previous").param("name", "Chest").param("before", "2026-09-05"))
                .andExpect(status().isUnauthorized());
        var session = account();
        mvc.perform(post("/api/workout-templates").session(session).contentType(MediaType.APPLICATION_JSON).content(TEMPLATE))
                .andExpect(status().isForbidden());
        for (String body : new String[]{TEMPLATE.replace(":6", ":0"), TEMPLATE.replace(":6", ":21"),
                TEMPLATE.replace("Bench", " "), "{\"name\":\"Chest\",\"exercises\":[null]}", TEMPLATE.replace("Chest", "Rest")}) {
            mvc.perform(post("/api/workout-templates").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    void workout(MockHttpSession session, String date, int reps) throws Exception {
        String body = """
                {"date":"%s","workout":"Back","exercises":[{"name":"Pull-ups","sets":[{"weightKg":null,"reps":%d}]}]}
                """.formatted(date, reps);
        mvc.perform(post("/api/workouts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());
    }

    @Test void comparisonSelectsLatestEarlierOwnSessionAndPreservesBodyweight() throws Exception {
        var alice = account();
        var bob = account();
        workout(alice, "2026-09-01", 8);
        workout(alice, "2026-09-03", 10);
        workout(alice, "2026-09-05", 12);
        workout(alice, "2026-09-06", 13);
        workout(bob, "2026-09-04", 99);
        mvc.perform(get("/api/workouts/previous").session(alice).param("name", " back ").param("before", "2026-09-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-09-03"))
                .andExpect(jsonPath("$.exercises[0].sets[0].weightKg").isEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].reps").value(10));
        mvc.perform(get("/api/workouts/previous").session(bob).param("name", "Back").param("before", "2026-09-04"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/workouts/previous").session(alice).param("name", "Chest").param("before", "2026-09-05"))
                .andExpect(status().isNoContent());
    }
}
