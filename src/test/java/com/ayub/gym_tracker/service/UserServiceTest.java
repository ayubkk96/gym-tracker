package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.dto.response.UserRegistrationResponse;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.entity.DailyTarget;
import com.ayub.gym_tracker.exception.EmailAlreadyExistsException;
import com.ayub.gym_tracker.repository.AppUserRepository;
import com.ayub.gym_tracker.repository.DailyTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private AppUserRepository appUserRepository;
    private DailyTargetRepository dailyTargetRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        dailyTargetRepository = mock(DailyTargetRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(
                appUserRepository,
                dailyTargetRepository,
                passwordEncoder
        );
    }

    @Test
    void normalizesEmailStoresPasswordHashAndStartsTrackingToday() {
        String rawPassword = "a-secure-password";
        String passwordHash = "$2a$10$encoded-password";
        UserRegistrationRequest request = request(rawPassword);

        when(appUserRepository.findByEmailIgnoreCase(
                "bob@example.com"
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword))
                .thenReturn(passwordHash);
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserRegistrationResponse response =
                userService.register(request);

        ArgumentCaptor<AppUser> userCaptor =
                ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(userCaptor.capture());

        AppUser savedUser = userCaptor.getValue();
        assertEquals("bob@example.com", savedUser.getEmail());
        assertEquals("Bob Example", savedUser.getDisplayName());
        assertEquals(passwordHash, savedUser.getPasswordHash());
        assertNotEquals(rawPassword, savedUser.getPasswordHash());
        assertTrue(savedUser.isEmailVerified());
        assertFalse(response.emailVerificationRequired());
        assertEquals("bob@example.com", response.email());
        assertEquals("Bob Example", response.displayName());
        assertEquals(LocalDate.now(), response.startDate());

        ArgumentCaptor<DailyTarget> targetCaptor =
                ArgumentCaptor.forClass(DailyTarget.class);
        verify(dailyTargetRepository).save(targetCaptor.capture());
        assertEquals(savedUser, targetCaptor.getValue().getUser());
        assertEquals(2450, targetCaptor.getValue().getCalories());
        assertEquals(LocalDate.now(), targetCaptor.getValue().getEffectiveFrom());
    }

    @Test
    void createsPendingAccountWhenEmailVerificationIsRequired() {
        UserRegistrationRequest request = request("a-secure-password");
        when(appUserRepository.findByEmailIgnoreCase("bob@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-secure-password"))
                .thenReturn("password-hash");
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserRegistrationResponse response = userService.register(request, true);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(userCaptor.capture());
        assertFalse(userCaptor.getValue().isEmailVerified());
        assertTrue(response.emailVerificationRequired());
    }

    @Test
    void convertsConcurrentDuplicateEmailsIntoAConflict() {
        UserRegistrationRequest request = request("a-secure-password");

        when(appUserRepository.findByEmailIgnoreCase(
                "bob@example.com"
        )).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-secure-password"))
                .thenReturn("password-hash");
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate email"
                ));

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.register(request)
        );

        verify(dailyTargetRepository, never())
                .save(any(DailyTarget.class));
    }

    private UserRegistrationRequest request(String password) {
        return new UserRegistrationRequest(
                "  Bob@Example.com ",
                "  Bob Example  ",
                password,
                new DailyTargetRequest(
                        2450,
                        new BigDecimal("180"),
                        new BigDecimal("275"),
                        new BigDecimal("75")
                )
        );
    }
}
