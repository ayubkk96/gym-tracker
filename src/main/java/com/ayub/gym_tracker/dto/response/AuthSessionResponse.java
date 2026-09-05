package com.ayub.gym_tracker.dto.response;

public record AuthSessionResponse(
        boolean authenticated,
        Long userId,
        String email,
        String displayName,
        String csrfToken,
        String csrfHeaderName
) {
}
