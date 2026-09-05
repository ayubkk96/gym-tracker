package com.ayub.gym_tracker.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkoutSetRequest(
        @DecimalMin("0.0") BigDecimal weightKg,
        @NotNull @Min(1) Integer reps
) {
}