package com.ayub.gym_tracker.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrackerImportRequest(
        @NotNull
        @Size(max = 366)
        List<@Valid NutritionRequest> nutrition,

        @NotNull
        @Size(max = 366)
        List<@Valid WorkoutRequest> workouts
) {
}
