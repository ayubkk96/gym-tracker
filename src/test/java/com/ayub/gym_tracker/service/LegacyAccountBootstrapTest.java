package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyAccountBootstrapTest {

    private final AppUserRepository appUserRepository =
            mock(AppUserRepository.class);
    private final PasswordEncoder passwordEncoder =
            mock(PasswordEncoder.class);
    private final ApplicationArguments arguments =
            mock(ApplicationArguments.class);

    @Test
    void doesNothingWhenBootstrapSettingsAreEmpty() {
        LegacyAccountBootstrap bootstrap =
                new LegacyAccountBootstrap(
                        appUserRepository,
                        passwordEncoder,
                        "",
                        ""
                );

        bootstrap.run(arguments);

        verifyNoInteractions(
                appUserRepository,
                passwordEncoder
        );
    }

    @Test
    void givesAnExistingAccountItsFirstPassword() {
        AppUser user = new AppUser(
                "bob@example.com",
                "Bob",
                null
        );
        String password = "a-secure-password";
        String hash = "$2a$10$encoded-password";
        LegacyAccountBootstrap bootstrap =
                new LegacyAccountBootstrap(
                        appUserRepository,
                        passwordEncoder,
                        "bob@example.com",
                        password
                );

        when(appUserRepository.findByEmailIgnoreCase(
                "bob@example.com"
        )).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(password)).thenReturn(hash);

        bootstrap.run(arguments);

        assertEquals(hash, user.getPasswordHash());
        verify(appUserRepository).save(user);
    }

    @Test
    void neverOverwritesAnExistingPassword() {
        AppUser user = new AppUser(
                "bob@example.com",
                "Bob",
                "existing-hash"
        );
        LegacyAccountBootstrap bootstrap =
                new LegacyAccountBootstrap(
                        appUserRepository,
                        passwordEncoder,
                        "bob@example.com",
                        "a-different-secure-password"
                );

        when(appUserRepository.findByEmailIgnoreCase(
                "bob@example.com"
        )).thenReturn(Optional.of(user));

        bootstrap.run(arguments);

        assertEquals("existing-hash", user.getPasswordHash());
        verify(passwordEncoder, never()).encode(
                "a-different-secure-password"
        );
        verify(appUserRepository, never()).save(user);
    }

    @Test
    void rejectsIncompleteBootstrapSettings() {
        LegacyAccountBootstrap bootstrap =
                new LegacyAccountBootstrap(
                        appUserRepository,
                        passwordEncoder,
                        "bob@example.com",
                        ""
                );

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.run(arguments)
        );
    }
}
