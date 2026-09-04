// WorkoutResponse.java
package com.ayub.gym_tracker.dto;

import java.util.List;

public record WorkoutResponse(
        String name,
        int day,
        List<ExerciseResponse> exercises
) {
}