package com.ayub.gym_tracker.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkoutRequest(
        LocalDate date,
        Integer day,
        String workout,
        List<ExerciseRequest> exercises
) {
}