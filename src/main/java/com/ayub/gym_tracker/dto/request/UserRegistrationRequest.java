package com.ayub.gym_tracker.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserRegistrationRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotNull
        @PastOrPresent
        LocalDate startDate,

        @NotNull
        @Valid
        DailyTargetRequest targets
) {
}