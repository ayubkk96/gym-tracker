package com.ayub.gym_tracker.dto;

public record WorkoutSaveResult(
        long id,
        int exercisesSaved,
        boolean created
) {
}