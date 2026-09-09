package com.ayub.gym_tracker;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.service.PasswordResetService;
import com.ayub.gym_tracker.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RememberMeIntegrationTests {
    private static final String PASSWORD = "Remember-me-password-123";
    private static final String REMEMBER_COOKIE = "gym-tracker-remember-me";

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private UserService users;
    @Autowired
    private PasswordResetService passwordResets;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String email;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        email = "remember-" + UUID.randomUUID() + "@example.com";
        createUser();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_users WHERE email = ?", email);
    }

    @Test
    void rememberMeAuthenticatesWithoutTheOriginalSession() throws Exception {
        MvcResult login = rememberLogin();
        Cookie remember = login.getResponse().getCookie(REMEMBER_COOKIE);

        assertNotNull(remember);
        assertEquals(30 * 24 * 60 * 60, remember.getMaxAge());
        assertEquals(1, persistentLoginCount());

        // No JSESSIONID is carried into this request: the persistent cookie alone
        // must restore the authenticated principal on the page security chain.
        mockMvc.perform(get("/").cookie(remember))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryLoginDoesNotCreateAPersistentLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(REMEMBER_COOKIE));

        assertEquals(0, persistentLoginCount());
    }

    @Test
    void logoutRevokesThePersistentLogin() throws Exception {
        MvcResult login = rememberLogin();
        Cookie remember = login.getResponse().getCookie(REMEMBER_COOKIE);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        assertNotNull(remember);
        assertNotNull(session);
        assertEquals(1, persistentLoginCount());

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(remember)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(REMEMBER_COOKIE, 0));

        assertEquals(0, persistentLoginCount());
    }

    @Test
    void passwordResetRevokesRememberedDevices() throws Exception {
        MvcResult login = rememberLogin();
        Cookie remember = login.getResponse().getCookie(REMEMBER_COOKIE);
        assertNotNull(remember);
        assertEquals(1, persistentLoginCount());

        PasswordResetService.Delivery reset = passwordResets.issue(email)
                .orElseThrow();
        assertTrue(passwordResets.confirm(
                reset.token(),
                "Replacement-password-456"
        ));
        assertEquals(0, persistentLoginCount());

        mockMvc.perform(get("/").cookie(remember))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));
    }

    private MvcResult rememberLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD)
                        .param("remember-me", "true"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(REMEMBER_COOKIE))
                .andReturn();
    }

    private int persistentLoginCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?",
                Integer.class,
                email
        );
        return count == null ? 0 : count;
    }

    private void createUser() {
        users.register(new UserRegistrationRequest(
                email,
                "Remember Tester",
                PASSWORD,
                new DailyTargetRequest(
                        2450,
                        new BigDecimal("180"),
                        new BigDecimal("275"),
                        new BigDecimal("75")
                )
        ));
    }
}
