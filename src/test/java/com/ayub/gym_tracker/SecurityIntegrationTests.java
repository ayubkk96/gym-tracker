package com.ayub.gym_tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void servesThePublicHomepageAndProtectsTheDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "My Gym Tracker – Free Workout and Nutrition Tracker"
                )));

        mockMvc.perform(get("/dashboard.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void servesCrawlerFilesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sitemap: https://mygymtracker.co.uk/sitemap.xml"
                )));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://mygymtracker.co.uk/"
                )));
    }

    @Test
    void returnsJsonUnauthorizedForPrivateApis() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .queryParam("date", "2026-09-05"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Authentication required."));
    }

    @Test
    void servesTheLoginPageAndAnonymousSession() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.csrfHeaderName")
                        .value("X-CSRF-TOKEN"));
    }

    @Test
    void servesTheDemoWithoutGrantingAccessToPrivateData() throws Exception {
        mockMvc.perform(get("/demo.html")).andExpect(status().isOk());
        mockMvc.perform(get("/demo.js")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard").queryParam("date", "2026-09-05"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void rejectsRegistrationWithoutCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
