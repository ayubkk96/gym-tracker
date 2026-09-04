package com.ayub.gym_tracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionRequest(
        @NotNull LocalDate date,
        @NotNull @Min(0) Integer calories,
        @NotNull @DecimalMin("0.0") BigDecimal proteinG,
        @NotNull @DecimalMin("0.0") BigDecimal carbsG,
        @NotNull @DecimalMin("0.0") BigDecimal fatG,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal weightKg,
        @Size(max = 250) String notes
) {
}