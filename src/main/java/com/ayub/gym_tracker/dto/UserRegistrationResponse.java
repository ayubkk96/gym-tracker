package com.ayub.gym_tracker.dto;

import java.time.LocalDate;

public record UserRegistrationResponse(
        Long userId,
        String email,
        String displayName,
        LocalDate startDate
) {
}