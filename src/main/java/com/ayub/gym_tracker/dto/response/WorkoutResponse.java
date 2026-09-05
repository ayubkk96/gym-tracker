// WorkoutResponse.java
package com.ayub.gym_tracker.dto.response;

import java.util.List;

public record WorkoutResponse(
        String name,
        Integer day,
        List<ExerciseResponse> exercises,
        String notes
) {
}
