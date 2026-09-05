package com.ayub.gym_tracker.config;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private final AppUserRepository appUserRepository =
            mock(AppUserRepository.class);
    private final UserDetailsService userDetailsService =
            new SecurityConfig().userDetailsService(appUserRepository);

    @Test
    void loadsAUserWithAConfiguredPassword() {
        AppUser user = new AppUser(
                "bob@example.com",
                "Bob",
                "password-hash"
        );

        when(appUserRepository.findByEmailIgnoreCase(
                "Bob@Example.com"
        )).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService
                .loadUserByUsername("Bob@Example.com");

        assertEquals("bob@example.com", details.getUsername());
        assertEquals("password-hash", details.getPassword());
        assertEquals(
                "ROLE_USER",
                details.getAuthorities().iterator().next()
                        .getAuthority()
        );
    }

    @Test
    void hidesAccountsThatDoNotHaveAPasswordYet() {
        AppUser legacyUser = new AppUser(
                "bob@example.com",
                "Bob",
                null
        );

        when(appUserRepository.findByEmailIgnoreCase(
                "bob@example.com"
        )).thenReturn(Optional.of(legacyUser));

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(
                        "bob@example.com"
                )
        );
    }
}
