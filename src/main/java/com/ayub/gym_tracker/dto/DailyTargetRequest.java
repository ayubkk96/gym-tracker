package com.ayub.gym_tracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DailyTargetRequest(
        @NotNull @Min(1) Integer calories,
        @NotNull @DecimalMin("0.0") BigDecimal proteinG,
        @NotNull @DecimalMin("0.0") BigDecimal carbsG,
        @NotNull @DecimalMin("0.0") BigDecimal fatG
) {
}