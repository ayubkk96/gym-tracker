package com.ayub.gym_tracker.dto.result;

public record TrackerImportResult(
        int nutritionCreated,
        int nutritionUpdated,
        int workoutsCreated,
        int workoutsUpdated
) {
}
