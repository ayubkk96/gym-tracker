package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.DailyTargetsResponse;
import com.ayub.gym_tracker.dto.DashboardResponse;
import com.ayub.gym_tracker.dto.NutritionResponse;
import com.ayub.gym_tracker.dto.WorkoutResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final NutritionService nutritionService;
    private final WorkoutService workoutService;

    public DashboardService(
            NutritionService nutritionService,
            WorkoutService workoutService
    ) {
        this.nutritionService = nutritionService;
        this.workoutService = workoutService;
    }

    public DashboardResponse getDashboard(
            LocalDate date
    ) throws IOException {
        DailyTargetsResponse targets =
                nutritionService.getDailyTargets(date);

        NutritionResponse nutrition =
                nutritionService.getNutritionByDate(date);

        List<WorkoutResponse> workouts =
                workoutService.getWorkoutsByDate(date);

        Integer day = findDay(nutrition, workouts);

        return new DashboardResponse(
                date,
                day,
                targets,
                nutrition,
                workouts
        );
    }

    private Integer findDay(
            NutritionResponse nutrition,
            List<WorkoutResponse> workouts
    ) {
        if (nutrition != null) {
            return nutrition.day();
        }

        if (!workouts.isEmpty()) {
            return workouts.get(0).day();
        }

        return null;
    }
}