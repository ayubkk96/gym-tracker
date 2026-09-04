package com.ayub.gym_tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExerciseRequest(
        @NotBlank @Size(max = 100) String name,

        @NotNull
        @Size(min = 1, max = 6)
        List<@Valid WorkoutSetRequest> sets,

        @Size(max = 250) String notes
) {
}