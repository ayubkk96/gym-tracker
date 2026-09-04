// WorkoutSetResponse.java
package com.ayub.gym_tracker.dto;

import java.math.BigDecimal;

public record WorkoutSetResponse(
        BigDecimal weightKg,
        int reps
) {
}