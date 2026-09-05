package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.NutritionRequest;
import com.ayub.gym_tracker.dto.request.TrackerImportRequest;
import com.ayub.gym_tracker.dto.request.WorkoutRequest;
import com.ayub.gym_tracker.dto.result.NutritionSaveResult;
import com.ayub.gym_tracker.dto.result.TrackerImportResult;
import com.ayub.gym_tracker.dto.result.WorkoutSaveResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackerImportService {

    private final NutritionService nutritionService;
    private final WorkoutService workoutService;

    public TrackerImportService(
            NutritionService nutritionService,
            WorkoutService workoutService
    ) {
        this.nutritionService = nutritionService;
        this.workoutService = workoutService;
    }

    @Transactional
    public TrackerImportResult importTracker(
            TrackerImportRequest request
    ) {
        int nutritionCreated = 0;
        int nutritionUpdated = 0;
        int workoutsCreated = 0;
        int workoutsUpdated = 0;

        for (NutritionRequest nutrition : request.nutrition()) {
            NutritionSaveResult result =
                    nutritionService.saveNutrition(nutrition);

            if (result.created()) {
                nutritionCreated++;
            } else {
                nutritionUpdated++;
            }
        }

        for (WorkoutRequest workout : request.workouts()) {
            WorkoutSaveResult result =
                    workoutService.saveWorkout(workout);

            if (result.created()) {
                workoutsCreated++;
            } else {
                workoutsUpdated++;
            }
        }

        return new TrackerImportResult(
                nutritionCreated,
                nutritionUpdated,
                workoutsCreated,
                workoutsUpdated
        );
    }
}
