package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.response.AuthSessionResponse;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/session")
    public AuthSessionResponse getSession(
            Authentication authentication,
            HttpServletRequest request
    ) {
        CsrfToken csrfToken = getCsrfToken(request);

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication
                instanceof AnonymousAuthenticationToken) {
            return new AuthSessionResponse(
                    false,
                    null,
                    null,
                    null,
                    csrfToken.getToken(),
                    csrfToken.getHeaderName()
            );
        }

        AppUser user = currentUserService.getCurrentUser();

        return new AuthSessionResponse(
                true,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                csrfToken.getToken(),
                csrfToken.getHeaderName()
        );
    }

    private CsrfToken getCsrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName()
        );

        if (token == null) {
            throw new IllegalStateException(
                    "CSRF protection is not available."
            );
        }

        return token;
    }
}
