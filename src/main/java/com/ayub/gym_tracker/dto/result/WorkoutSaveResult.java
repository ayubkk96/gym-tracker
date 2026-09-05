package com.ayub.gym_tracker.dto.result;

public record WorkoutSaveResult(
        long id,
        int exercisesSaved,
        boolean created
) {
}