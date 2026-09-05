package com.ayub.gym_tracker.config;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            AppUserRepository appUserRepository
    ) {
        return email -> {
            AppUser user = appUserRepository
                    .findByEmailIgnoreCase(email)
                    .filter(AppUser::hasPassword)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Invalid email or password."
                            )
                    );

            return User.withUsername(user.getEmail())
                    .password(user.getPasswordHash())
                    .roles("USER")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login.html",
                                "/auth.js",
                                "/styles.css",
                                "/favicon.ico",
                                "/error",
                                "/api/health",
                                "/api/auth/session",
                                "/api/users"
                        )
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(
                                        response,
                                        HttpServletResponse.SC_OK,
                                        "{\"authenticated\":true}"
                                )
                        )
                        .failureHandler((request, response, exception) ->
                                writeJson(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "{\"authenticated\":false,"
                                                + "\"message\":"
                                                + "\"Invalid email or password.\"}"
                                )
                        )
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(
                                        response,
                                        HttpServletResponse.SC_OK,
                                        "{\"authenticated\":false}"
                                )
                        )
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        writeJson(
                                                response,
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "{\"message\":"
                                                        + "\"Authentication required.\"}"
                                        ),
                                PathPatternRequestMatcher
                                        .withDefaults()
                                        .matcher("/api/**")
                        )
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(policy -> policy
                                .policyDirectives(
                                        "default-src 'self'; "
                                                + "base-uri 'self'; "
                                                + "form-action 'self'; "
                                                + "frame-ancestors 'none'; "
                                                + "object-src 'none'"
                                )
                        )
                );

        return http.build();
    }

    private void writeJson(
            HttpServletResponse response,
            int status,
            String body
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}
