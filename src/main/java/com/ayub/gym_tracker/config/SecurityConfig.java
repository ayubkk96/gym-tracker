package com.ayub.gym_tracker.config;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import com.ayub.gym_tracker.security.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

@Configuration
public class SecurityConfig {
    private static final int REMEMBER_ME_SECONDS = 30 * 24 * 60 * 60;
    private static final String REMEMBER_ME_COOKIE = "gym-tracker-remember-me";

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
                    .filter(AppUser::isEmailVerified)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Invalid email or password."
                            )
                    );

            return new TrackerPrincipal(user);
        };
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(
            DataSource dataSource
    ) {
        JdbcTokenRepositoryImpl repository =
                new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            AuthRateLimiter limiter,
            AppUserRepository users,
            UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokens
    ) throws Exception {
        http
                .securityMatcher("/api/**")
                .addFilterBefore(new AuthRateLimitFilter(limiter), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new PasswordSessionFilter(users), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/session",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/api/auth/email-verification/request",
                                "/api/auth/email-verification/confirm",
                                "/api/users"
                        )
                        .permitAll()
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
                .rememberMe(remember -> remember
                        .tokenRepository(persistentTokens)
                        .userDetailsService(userDetailsService)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .rememberMeCookieName(REMEMBER_ME_COOKIE)
                        .rememberMeParameter("remember-me")
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
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        writeJson(
                                                response,
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "{\"message\":"
                                                        + "\"Authentication required.\"}"
                                        )
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

    @Bean
    @Order(2)
    public SecurityFilterChain pageSecurityFilterChain(
            HttpSecurity http,
            AppUserRepository users,
            UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokens
    ) throws Exception {
        http
                .addFilterAfter(new PasswordSessionFilter(users), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login.html",
                                "/demo.html",
                                "/demo.js",
                                "/auth.js",
                                "/reset-password.html",
                                "/reset-password.js",
                                "/verify-email.html",
                                "/verify-email.js",
                                "/styles.css",
                                "/favicon.ico",
                                "/error"
                        )
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .rememberMe(remember -> remember
                        .tokenRepository(persistentTokens)
                        .userDetailsService(userDetailsService)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .rememberMeCookieName(REMEMBER_ME_COOKIE)
                        .rememberMeParameter("remember-me")
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint(
                                        "/login.html"
                                )
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
