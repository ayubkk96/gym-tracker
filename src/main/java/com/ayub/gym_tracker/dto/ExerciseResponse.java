// ExerciseResponse.java
package com.ayub.gym_tracker.dto;

import java.util.List;

public record ExerciseResponse(
        String name,
        List<WorkoutSetResponse> sets,
        int totalReps,
        String notes
) {
}