package com.ayub.gym_tracker.dto;

public record WorkoutSaveResult(
        int exercisesSaved,
        String updatedRange,
        boolean created
) {
}