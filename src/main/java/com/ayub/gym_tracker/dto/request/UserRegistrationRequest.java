package com.ayub.gym_tracker.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Size(min = 12, max = 64)
        String password,

        @NotNull
        @Valid
        DailyTargetRequest targets
) {
}
