package com.ayub.gym_tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record WorkoutRequest(
        @NotNull
        LocalDate date,

        @NotBlank
        @Size(max = 100)
        String workout,

        @NotNull
        @Size(min = 1, max = 20)
        List<@Valid ExerciseRequest> exercises
) {
}