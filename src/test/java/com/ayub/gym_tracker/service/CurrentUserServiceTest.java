package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    private final AppUserRepository appUserRepository =
            mock(AppUserRepository.class);
    private final CurrentUserService currentUserService =
            new CurrentUserService(appUserRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheUserFromTheAuthenticatedEmail() {
        AppUser user = mock(AppUser.class);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "Bob@Example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        when(appUserRepository.findByEmailIgnoreCase(
                "Bob@Example.com"
        )).thenReturn(Optional.of(user));

        assertSame(user, currentUserService.getCurrentUser());
        verify(appUserRepository).findByEmailIgnoreCase(
                "Bob@Example.com"
        );
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedUser() {
        SecurityContextHolder.clearContext();

        assertThrows(
                IllegalStateException.class,
                currentUserService::getCurrentUser
        );
    }
}
