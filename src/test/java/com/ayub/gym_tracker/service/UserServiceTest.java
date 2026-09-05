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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void normalizesEmailAndStoresOnlyThePasswordHash() {
        String rawPassword = "a-secure-password";
        String passwordHash = "$2a$10$encoded-password";
        LocalDate startDate = LocalDate.of(2026, 9, 5);
        UserRegistrationRequest request =
                new UserRegistrationRequest(
                        "  Bob@Example.com ",
                        "  Bob Example  ",
                        rawPassword,
                        startDate,
                        new DailyTargetRequest(
                                2450,
                                new BigDecimal("180"),
                                new BigDecimal("275"),
                                new BigDecimal("75")
                        )
                );

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
        assertEquals("bob@example.com", response.email());
        assertEquals("Bob Example", response.displayName());
        assertEquals(startDate, response.startDate());

        ArgumentCaptor<DailyTarget> targetCaptor =
                ArgumentCaptor.forClass(DailyTarget.class);
        verify(dailyTargetRepository).save(targetCaptor.capture());
        assertEquals(savedUser, targetCaptor.getValue().getUser());
        assertEquals(2450, targetCaptor.getValue().getCalories());
    }

    @Test
    void convertsConcurrentDuplicateEmailsIntoAConflict() {
        UserRegistrationRequest request =
                new UserRegistrationRequest(
                        "bob@example.com",
                        "Bob",
                        "a-secure-password",
                        LocalDate.of(2026, 9, 5),
                        new DailyTargetRequest(
                                2450,
                                new BigDecimal("180"),
                                new BigDecimal("275"),
                                new BigDecimal("75")
                        )
                );

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
}
