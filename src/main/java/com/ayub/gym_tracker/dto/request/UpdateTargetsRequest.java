package com.ayub.gym_tracker.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateTargetsRequest(
        @NotNull LocalDate effectiveFrom,
        @NotNull @Min(1) Integer calories,
        @NotNull @Min(0) @Max(9999) Integer proteinG,
        @NotNull @Min(0) @Max(9999) Integer carbsG,
        @NotNull @Min(0) @Max(9999) Integer fatG
) {}
