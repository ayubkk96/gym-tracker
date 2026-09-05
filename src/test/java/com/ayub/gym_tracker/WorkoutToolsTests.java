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
