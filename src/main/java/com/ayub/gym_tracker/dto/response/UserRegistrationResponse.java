package com.ayub.gym_tracker.dto.response;

import java.time.LocalDate;

public record UserRegistrationResponse(
        Long userId,
        String email,
        String displayName,
        LocalDate startDate,
        boolean emailVerificationRequired
) {
    public UserRegistrationResponse(
            Long userId,
            String email,
            String displayName,
            LocalDate startDate
    ) {
        this(userId, email, displayName, startDate, false);
    }
}
