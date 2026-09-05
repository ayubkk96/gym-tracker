package com.ayub.gym_tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderDeploymentTest {
    private MockEnvironment environment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new ResourcePropertySource(
                "classpath:application-render.properties"));
        environment.getPropertySources().addLast(new ResourcePropertySource(
                "classpath:application.properties"));
        return environment;
    }

    @Test
    void buildsJdbcUrlFromSeparateDatabaseProperties() throws IOException {
        MockEnvironment environment = environment()
                .withProperty("DB_HOST", "private-db")
                .withProperty("DB_PORT", "5432")
                .withProperty("DB_NAME", "gym_tracker")
                .withProperty("DB_USERNAME", "test-user")
                .withProperty("DB_PASSWORD", "test-only-password");
        assertEquals("jdbc:postgresql://private-db:5432/gym_tracker",
                environment.getRequiredProperty("spring.datasource.url"));
        assertEquals("test-user", environment.getRequiredProperty("spring.datasource.username"));
        assertEquals("test-only-password", environment.getRequiredProperty("spring.datasource.password"));
    }

    @Test
    void missingDatabaseConfigurationDoesNotFallBackToLocalCredentials() throws IOException {
        MockEnvironment environment = environment();
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.url"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.username"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.password"));
    }

    @Test
    void respectsHostingPortAndUsesSecureCookies() throws IOException {
        MockEnvironment environment = environment();
        assertEquals("10000", environment.getRequiredProperty("server.port"));
        environment.withProperty("PORT", "12345").withProperty("SESSION_COOKIE_SECURE", "false");
        assertEquals("12345", environment.getRequiredProperty("server.port"));
        assertEquals("0.0.0.0", environment.getRequiredProperty("server.address"));
        assertEquals("true", environment.getRequiredProperty("server.servlet.session.cookie.secure"));
        assertEquals("true", environment.getRequiredProperty("server.servlet.session.cookie.http-only"));
        assertEquals("validate", environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", environment.getRequiredProperty("spring.flyway.enabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blueprintWiresDatabasePropertiesWithoutHardcodedSecrets() throws IOException {
        Map<String, Object> blueprint;
        try (var input = Files.newInputStream(Path.of("render.yaml"))) {
            blueprint = new Yaml().load(input);
        }
        var services = (List<Map<String, Object>>) blueprint.get("services");
        var databases = (List<Map<String, Object>>) blueprint.get("databases");
        assertEquals(1, services.size());
        assertEquals(1, databases.size());
        var service = services.getFirst();
        var database = databases.getFirst();
        assertEquals("docker", service.get("runtime"));
        assertEquals("checksPass", service.get("autoDeployTrigger"));
        assertEquals("/api/health", service.get("healthCheckPath"));
        assertEquals(service.get("region"), database.get("region"));
        assertEquals("17", database.get("postgresMajorVersion"));
        assertEquals(List.of(), database.get("ipAllowList"));
        assertNotEquals("free", database.get("plan"));
        var expected = Map.of("DB_HOST", "host", "DB_PORT", "port", "DB_NAME", "database",
                "DB_USERNAME", "user", "DB_PASSWORD", "password");
        var variables = (List<Map<String, Object>>) service.get("envVars");
        for (var entry : expected.entrySet()) {
            var variable = variables.stream().filter(value -> entry.getKey().equals(value.get("key")))
                    .findFirst().orElseThrow();
            assertFalse(variable.containsKey("value"));
            assertEquals(Map.of("name", database.get("name"), "property", entry.getValue()),
                    variable.get("fromDatabase"));
        }
        assertTrue(variables.stream().anyMatch(value -> "SPRING_PROFILES_ACTIVE".equals(value.get("key"))
                && "render".equals(value.get("value"))));
    }
}
