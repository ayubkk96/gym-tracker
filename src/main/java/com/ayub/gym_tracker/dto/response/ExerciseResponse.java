// ExerciseResponse.java
package com.ayub.gym_tracker.dto.response;

import java.util.List;

public record ExerciseResponse(
        String name,
        List<WorkoutSetResponse> sets,
        int totalReps,
        String notes
) {
}